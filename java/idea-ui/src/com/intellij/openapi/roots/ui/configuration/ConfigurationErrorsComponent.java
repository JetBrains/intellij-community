/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.roots.ui.configuration;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.ui.components.labels.LinkListener;
import com.intellij.util.SystemProperties;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.BaseButtonBehavior;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.TimedDeadzone;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;

/**
 * User: spLeaner
 */
public class ConfigurationErrorsComponent extends JPanel implements Disposable, ListDataListener {
  private static final int MAX_ERRORS_TO_SHOW = SystemProperties.getIntProperty("idea.project.structure.max.errors.to.show", 100);
  private static final boolean ONE_LINE = true;
  private static final boolean MULTI_LINE = false;

  @NonNls private static final String FIX_ACTION_NAME = "FIX";
  @NonNls private static final String NAVIGATE_ACTION_NAME = "NAVIGATE";

  private ConfigurationErrorsListModel myConfigurationErrorsListModel;
  private ErrorView myCurrentView;

  public ConfigurationErrorsComponent(@NotNull final Project project) {
    setLayout(new BorderLayout());
    myConfigurationErrorsListModel = new ConfigurationErrorsListModel(project);
    myConfigurationErrorsListModel.addListDataListener(this);

    addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(final ComponentEvent e) {
        revalidate();
        repaint();
      }
    });

    ensureCurrentViewIs(ONE_LINE, null);
    Disposer.register(this, myConfigurationErrorsListModel);
  }

  @Override
  public void dispose() {
    if (myConfigurationErrorsListModel != null) {
      myConfigurationErrorsListModel.removeListDataListener(this);
      myConfigurationErrorsListModel = null;
    }
  }

  private void ensureCurrentViewIs(final boolean oneLine, @Nullable final Object data) {
    if (oneLine) {
      if (myCurrentView instanceof OneLineErrorComponent) return;
      myConfigurationErrorsListModel.setFilter(true, true);
      OneLineErrorComponent c = new OneLineErrorComponent(myConfigurationErrorsListModel) {
        @Override
        public void onViewChange(Object data) {
          ensureCurrentViewIs(MULTI_LINE, data);
        }
      };

      if (myCurrentView != null) {
        remove(myCurrentView.self());
        Disposer.dispose(myCurrentView);
      }

      myCurrentView = c;
    }
    else {
      myConfigurationErrorsListModel.setFilter(data == null || !"Ignored".equals(data), data == null || "Ignored".equals(data));
      if (myCurrentView instanceof MultiLineErrorComponent) return;
        MultiLineErrorComponent c = new MultiLineErrorComponent(myConfigurationErrorsListModel) {
          @Override
          public void onViewChange(Object data) {
            ensureCurrentViewIs(ONE_LINE, data);
          }
        };

      if (myCurrentView != null) {
        remove(myCurrentView.self());
        Disposer.dispose(myCurrentView);
      }

      myCurrentView = c;
    }

    add(myCurrentView.self(), BorderLayout.CENTER);
    myCurrentView.updateView();
    UIUtil.adjustWindowToMinimumSize(SwingUtilities.getWindowAncestor(this));
    revalidate();
    repaint();
  }

  @Override
  public void intervalAdded(final ListDataEvent e) {
    updateCurrentView();
  }

  @Override
  public void intervalRemoved(final ListDataEvent e) {
    updateCurrentView();
  }

  @Override
  public void contentsChanged(final ListDataEvent e) {
    updateCurrentView();
  }

  private void updateCurrentView() {
    if (myCurrentView instanceof MultiLineErrorComponent && myConfigurationErrorsListModel.getSize() == 0) {
      ensureCurrentViewIs(ONE_LINE, null);
    }

    myCurrentView.updateView();
  }

  private interface ErrorView extends Disposable {
    void updateView();
    void onViewChange(Object data);
    JComponent self();
  }

  private abstract static class MultiLineErrorComponent extends JPanel implements ErrorView {
    private JList myList = new JBList();

    protected MultiLineErrorComponent(@NotNull final ConfigurationErrorsListModel model) {
      setLayout(new BorderLayout());
      setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));

      myList.setModel(model);
      myList.setCellRenderer(new ErrorListRenderer(myList));
      myList.setBackground(UIUtil.getPanelBackground());

      myList.addMouseListener(new MouseAdapter() {
        @Override
        public void mouseClicked(final MouseEvent e) {
          if (!e.isPopupTrigger()) {
            processListMouseEvent(e, true);
          }
        }
      });
      
      myList.addMouseMotionListener(new MouseAdapter() {
        @Override
        public void mouseMoved(MouseEvent e) {
          if (!e.isPopupTrigger()) {
            processListMouseEvent(e, false);
          }
        }
      });

      myList.addComponentListener(new ComponentAdapter() {
        @Override
        public void componentResized(ComponentEvent e) {
          myList.setCellRenderer(new ErrorListRenderer(myList)); // request cell renderer size invalidation
          updatePreferredSize();
        }
      });

      add(new JBScrollPane(myList), BorderLayout.CENTER);
      add(buildToolbar(), BorderLayout.WEST);
    }

    @Override
    public void dispose() {
    }

    private void processListMouseEvent(final MouseEvent e, final boolean click) {
      final int index = myList.locationToIndex(e.getPoint());
      if (index > -1) {
        final Object value = myList.getModel().getElementAt(index);
        if (value != null && value instanceof ConfigurationError) {
          final ConfigurationError error = (ConfigurationError)value;
          final Component renderer = myList.getCellRenderer().getListCellRendererComponent(myList, value, index, false, false);
          if (renderer instanceof ErrorListRenderer) {
            final Rectangle bounds = myList.getCellBounds(index, index);
            renderer.setBounds(bounds);
            renderer.doLayout();

            final Point point = e.getPoint();
            point.translate(-bounds.x, -bounds.y);

            final Component deepestComponentAt = SwingUtilities.getDeepestComponentAt(renderer, point.x, point.y);
            if (deepestComponentAt instanceof ToolbarAlikeButton) {
              final String name = ((ToolbarAlikeButton)deepestComponentAt).getButtonName();
              if (click) {
                if (FIX_ACTION_NAME.equals(name)) {
                  onClickFix(error, (JComponent)deepestComponentAt, e);
                }
                else if (NAVIGATE_ACTION_NAME.equals(name)) {
                  error.navigate();
                }
                else {
                  onClickIgnore(error);
                }
              }
              else {
                myList.setToolTipText(FIX_ACTION_NAME.equals(name) ? "Fix" : NAVIGATE_ACTION_NAME.equals(name) ? "Navigate to the problem" :
                                                                             error.isIgnored() ? "Not ignore this error" : "Ignore this error");
                return;
              }
            } else {
              if (e.getClickCount() == 2) {
                error.navigate();
              }
            }
          }
        }
      }
      
      myList.setToolTipText(null);
    }

    private void onClickIgnore(@NotNull final ConfigurationError error) {
      error.ignore(!error.isIgnored());
      final ListModel model = myList.getModel();
      if (model instanceof ConfigurationErrorsListModel) {
        ((ConfigurationErrorsListModel)model).update(error);
      }
    }

    private void onClickFix(@NotNull final ConfigurationError error, JComponent component, MouseEvent e) {
      error.fix(component, new RelativePoint(e));
    }

    @Override
    public void addNotify() {
      super.addNotify();
      updatePreferredSize();
    }

    private void updatePreferredSize() {
      final Window window = SwingUtilities.getWindowAncestor(this);
      if (window != null) {
        final Dimension d = window.getSize();
        final Dimension preferredSize = getPreferredSize();
        setPreferredSize(new Dimension(preferredSize.width, 200));
        setMinimumSize(getPreferredSize());
      }
    }

    private JComponent buildToolbar() {
      final JPanel result = new JPanel();
      result.setBorder(BorderFactory.createEmptyBorder(5, 0, 0, 0));
      result.setLayout(new BorderLayout());
      result.add(new ToolbarAlikeButton(AllIcons.Actions.Collapseall) {
        {
          setToolTipText("Collapse");
        }

        @Override
        public void onClick(MouseEvent e) {
          onViewChange(null);
        }
      }, BorderLayout.NORTH);

      return result;
    }

    @Override
    public void updateView() {
    }

    @Override
    public JComponent self() {
      return this;
    }

    @Override
    public abstract void onViewChange(@Nullable Object data);
  }

  private abstract static class ToolbarAlikeButton extends JComponent {
    private BaseButtonBehavior myBehavior;
    private Icon myIcon;
    private String myName;

    private ToolbarAlikeButton(@NotNull final Icon icon, @NotNull final String name) {
      this(icon);
      myName = name;
    }

    private ToolbarAlikeButton(@NotNull final Icon icon) {
      myIcon = icon;

      myBehavior = new BaseButtonBehavior(this, TimedDeadzone.NULL) {
        @Override
        protected void execute(MouseEvent e) {
          onClick(e);
        }
      };

      setOpaque(false);
    }

    public String getButtonName() {
      return myName;
    }

    public void onClick(MouseEvent e) {}

    @Override
    public Insets getInsets() {
      return new Insets(2, 2, 2, 2);
    }

    @Override
    public Dimension getPreferredSize() {
      return getMinimumSize();
    }

    @Override
    public Dimension getMinimumSize() {
      Dimension size = new Dimension(myIcon.getIconWidth(), myIcon.getIconHeight());
      JBInsets.addTo(size, getInsets());
      return size;
    }

    @Override
    public void paint(final Graphics g) {
      Rectangle bounds = new Rectangle(getWidth(), getHeight());
      JBInsets.removeFrom(bounds, getInsets());

      bounds.x += (bounds.width - myIcon.getIconWidth()) / 2;
      bounds.y += (bounds.height - myIcon.getIconHeight()) / 2;

      if (myBehavior.isHovered()) {
        // todo
      }

      if (myBehavior.isPressedByMouse()) {
        bounds.x++;
        bounds.y++;
      }

      myIcon.paintIcon(this, g, bounds.x, bounds.y);
    }
  }

  private static class ErrorListRenderer extends JComponent implements ListCellRenderer {
    private boolean mySelected;
    private boolean myHasFocus;
    private JTextPane myText;
    private JTextPane myFakeTextPane;
    private JViewport myFakeViewport;
    private JList myList;
    private JPanel myButtonsPanel;
    private JPanel myFixGroup;

    private ErrorListRenderer(@NotNull final JList list) {
      setLayout(new BorderLayout());
      setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
      setOpaque(false);

      myList = list;

      myText = new JTextPane();

      myButtonsPanel = new JPanel(new BorderLayout());
      myButtonsPanel.setBorder(BorderFactory.createEmptyBorder(5, 3, 5, 3));
      myButtonsPanel.setOpaque(false);
      final JPanel buttons = new JPanel();
      buttons.setOpaque(false);
      buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));
      myButtonsPanel.add(buttons, BorderLayout.NORTH);
      add(myButtonsPanel, BorderLayout.EAST);

      myFixGroup = new JPanel();
      myFixGroup.setOpaque(false);
      myFixGroup.setLayout(new BoxLayout(myFixGroup, BoxLayout.Y_AXIS));

      myFixGroup.add(new ToolbarAlikeButton(AllIcons.Actions.QuickfixBulb, FIX_ACTION_NAME) {});
      myFixGroup.add(Box.createHorizontalStrut(3));
      buttons.add(myFixGroup);

      buttons.add(new ToolbarAlikeButton(AllIcons.General.AutoscrollToSource, NAVIGATE_ACTION_NAME) {});
      buttons.add(Box.createHorizontalStrut(3));

      buttons.add(new ToolbarAlikeButton(AllIcons.Actions.Cancel, "IGNORE") {});

      myFakeTextPane = new JTextPane();
      myText.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));
      myFakeTextPane.setBorder(BorderFactory.createEmptyBorder(3, 0, 3, 0));
      myText.setOpaque(false);
      if (UIUtil.isUnderNimbusLookAndFeel()) {
        myText.setBackground(UIUtil.TRANSPARENT_COLOR);
      }

      myText.setEditable(false);
      myFakeTextPane.setEditable(false);
      myText.setEditorKit(UIUtil.getHTMLEditorKit());
      myFakeTextPane.setEditorKit(UIUtil.getHTMLEditorKit());

      myFakeViewport = new JViewport();
      myFakeViewport.setView(myFakeTextPane);

      add(myText, BorderLayout.CENTER);
    }

    @Override
    public Dimension getPreferredSize() {
      final Container parent = myList.getParent();
      if (parent != null) {
        myFakeTextPane.setText(myText.getText());
        final Dimension size = parent.getSize();
        myFakeViewport.setSize(size);
        final Dimension preferredSize = myFakeTextPane.getPreferredSize();

        final Dimension buttonsPrefSize = myButtonsPanel.getPreferredSize();
        final int maxHeight = Math.max(buttonsPrefSize.height, preferredSize.height);

        final Insets insets = getInsets();
        return new Dimension(Math.min(size.width - 20, preferredSize.width), maxHeight + insets.top + insets.bottom);
      }

      return super.getPreferredSize();
    }

    @Override
    public Component getListCellRendererComponent(final JList list, final Object value, final int index, final boolean isSelected, final boolean cellHasFocus) {
      final ConfigurationError error = (ConfigurationError)value;

      myList = list;

      mySelected = isSelected;
      myHasFocus = cellHasFocus;

      myFixGroup.setVisible(error.canBeFixed());

      myText.setText(error.getDescription());

      setBackground(error.isIgnored() ? MessageType.WARNING.getPopupBackground() : MessageType.ERROR.getPopupBackground());
      return this;
    }

    @Override
    protected void paintComponent(Graphics g) {
      final Graphics2D g2d = (Graphics2D)g;

      final Rectangle bounds = getBounds();
      final Insets insets = getInsets();

      final GraphicsConfig cfg = new GraphicsConfig(g);
      cfg.setAntialiasing(true);

      final Shape shape = new RoundRectangle2D.Double(insets.left, insets.top, bounds.width - 1 - insets.left - insets.right,
                                                      bounds.height - 1 - insets.top - insets.bottom, 6, 6);

      if (mySelected) {
        g2d.setColor(UIUtil.getListSelectionBackground());
        g2d.fillRect(0, 0, bounds.width, bounds.height);
      }

      g2d.setColor(Color.WHITE);
      g2d.fill(shape);


      Color bgColor = getBackground();

      g2d.setColor(bgColor);
      g2d.fill(shape);

      g2d.setColor(myHasFocus || mySelected ? getBackground().darker().darker() : getBackground().darker());
      g2d.draw(shape);
      cfg.restore();

      super.paintComponent(g);
    }
  }

  private abstract static class OneLineErrorComponent extends JComponent implements ErrorView, LinkListener {
    private LinkLabel myErrorsLabel = new LinkLabel(null, null);
    private LinkLabel myIgnoredErrorsLabel = new LinkLabel(null, null);
    private JLabel mySingleErrorLabel = new JLabel();

    private ConfigurationErrorsListModel myModel;

    private OneLineErrorComponent(@NotNull final ConfigurationErrorsListModel model) {
      myModel = model;

      setLayout(new BorderLayout());
      setOpaque(true);

      updateLabel(myErrorsLabel, MessageType.ERROR.getPopupBackground(), this, "Errors");
      updateLabel(mySingleErrorLabel, MessageType.ERROR.getPopupBackground(), null, null);
      updateLabel(myIgnoredErrorsLabel, MessageType.WARNING.getPopupBackground(), this, "Ignored");
    }

    @Override
    public void dispose() {
      myModel = null;
    }

    private static void updateLabel(@NotNull final JLabel label, @NotNull final Color bgColor, @Nullable final LinkListener listener, @Nullable Object linkData) {
      label.setBorder(BorderFactory.createEmptyBorder(3, 5, 3, 5));
      label.setOpaque(true);
      label.setBackground(bgColor);
      if (label instanceof LinkLabel) {
        ((LinkLabel)label).setListener(listener, linkData);
      }
    }

    @Override
    public void updateView() {
      if (myModel.getSize() == 0) {
        setBorder(null);
      } else {
        if (getBorder() == null) setBorder(
          BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(5, 0, 5, 0, UIUtil.getPanelBackground()),
                                             BorderFactory.createLineBorder(UIUtil.getPanelBackground().darker())));
      }

      final List<ConfigurationError> errors = myModel.getErrors();
      if (errors.size() > 0) {
        if (errors.size() == 1) {
          mySingleErrorLabel.setText(myModel.getErrors().get(0).getPlainTextTitle());
        } else {
          myErrorsLabel.setText(String.format("%s errors found", getErrorsCount(errors.size())));
        }
      }

      final List<ConfigurationError> ignoredErrors = myModel.getIgnoredErrors();
      if (ignoredErrors.size() > 0) {
        myIgnoredErrorsLabel.setText(String.format("%s ignored error%s", getErrorsCount(ignoredErrors.size()), ignoredErrors.size() == 1 ? "" : "s"));
      }

      removeAll();
      if (errors.size() > 0) {
        if (errors.size() == 1) {
          add(wrapLabel(mySingleErrorLabel, errors.get(0)), BorderLayout.CENTER);
          mySingleErrorLabel.setToolTipText(errors.get(0).getDescription());
        } else {
          add(myErrorsLabel, BorderLayout.CENTER);
        }
      }

      if (ignoredErrors.size() > 0) {
        add(myIgnoredErrorsLabel, errors.size() > 0 ? BorderLayout.EAST : BorderLayout.CENTER);
      }

      revalidate();
      repaint();
    }

    private static String getErrorsCount(final int size) {
      return size < MAX_ERRORS_TO_SHOW ? String.valueOf(size) : MAX_ERRORS_TO_SHOW + "+";
    }

    private JComponent wrapLabel(@NotNull final JLabel label, @NotNull final ConfigurationError configurationError) {
      final JPanel result = new JPanel(new BorderLayout());
      result.setBackground(label.getBackground());
      result.add(label, BorderLayout.CENTER);

      final JPanel buttonsPanel = new JPanel();
      buttonsPanel.setOpaque(false);
      buttonsPanel.setLayout(new BoxLayout(buttonsPanel, BoxLayout.X_AXIS));

      if (configurationError.canBeFixed()) {
        buttonsPanel.add(new ToolbarAlikeButton(AllIcons.Actions.QuickfixBulb) {
          {
            setToolTipText("Fix error");
          }

          @Override
          public void onClick(MouseEvent e) {
            final Object o = myModel.getElementAt(0);
            if (o instanceof ConfigurationError) {
              ((ConfigurationError)o).fix(this, new RelativePoint(e));
              updateView();
              final Container ancestor = SwingUtilities.getAncestorOfClass(ConfigurationErrorsComponent.class, this);
              if (ancestor != null && ancestor instanceof JComponent) {
                ((JComponent)ancestor).revalidate();
                ancestor.repaint();
              }
            }
          }
        });

        buttonsPanel.add(Box.createHorizontalStrut(3));
      }

      buttonsPanel.add(new ToolbarAlikeButton(AllIcons.General.AutoscrollToSource) {
        {
          setToolTipText("Navigate to error");
        }

        @Override
        public void onClick(MouseEvent e) {
          final Object o = myModel.getElementAt(0);
          if (o instanceof ConfigurationError) {
            ((ConfigurationError)o).navigate();
          }
        }
      });

      buttonsPanel.add(Box.createHorizontalStrut(3));

      buttonsPanel.add(new ToolbarAlikeButton(AllIcons.Actions.Cancel) {
        {
          setToolTipText("Ignore error");
        }

        @Override
        public void onClick(MouseEvent e) {
          final Object o = myModel.getElementAt(0);
          if (o instanceof ConfigurationError) {
            final ConfigurationError error = (ConfigurationError)o;
            error.ignore(!error.isIgnored());
            myModel.update(error);
            updateView();
          }
        }
      });
      buttonsPanel.add(Box.createHorizontalStrut(5));

      result.add(buttonsPanel, BorderLayout.EAST);

      return result;
    }

    @Override
    public JComponent self() {
      return this;
    }

    @Override
    public abstract void onViewChange(Object data);

    @Override
    public void linkSelected(LinkLabel aSource, Object data) {
      onViewChange(data);
    }
  }

  //todo[nik] move to ContainerUtil after 11.1
  @NotNull
  private static <T> List<T> concat(@NotNull final List<? extends T> list1, @NotNull final List<? extends T> list2) {
    return new AbstractList<T>() {
      @Override
      public T get(int index) {
        if (index < list1.size()) {
          return list1.get(index);
        }

        return list2.get(index - list1.size());
      }

      @Override
      public int size() {
        return list1.size() + list2.size();
      }
    };
  }

  private static class ConfigurationErrorsListModel extends AbstractListModel implements ConfigurationErrors, Disposable {
    private MessageBusConnection myConnection;
    private List<ConfigurationError> myNotIgnoredErrors = new ArrayList<ConfigurationError>();
    private List<ConfigurationError> myAllErrors;
    private List<ConfigurationError> myIgnoredErrors = new ArrayList<ConfigurationError>();

    private ConfigurationErrorsListModel(@NotNull final Project project) {
      setFilter(true, true);
      myConnection = project.getMessageBus().connect();
      myConnection.subscribe(TOPIC, this);
    }
    
    public void setFilter(boolean showNotIgnored, boolean showIgnored) {
      if (showIgnored && showNotIgnored) {
        myAllErrors = concat(myNotIgnoredErrors, myIgnoredErrors);
      }
      else if (showIgnored) {
        myAllErrors = myIgnoredErrors;
      }
      else {
        myAllErrors = myNotIgnoredErrors;
      }
    }

    @Override
    public int getSize() {
      return Math.min(myAllErrors.size(), MAX_ERRORS_TO_SHOW);
    }

    @Override
    public Object getElementAt(int index) {
      return myAllErrors.get(index);
    }
    
    @Override
    public void addError(@NotNull ConfigurationError error) {
      if (!myAllErrors.contains(error)) {
        List<ConfigurationError> targetList = error.isIgnored() ? myIgnoredErrors : myNotIgnoredErrors;
        if (targetList.size() < MAX_ERRORS_TO_SHOW) {
          targetList.add(0, error);
        }
        else {
          targetList.add(error);
        }

        int i = myAllErrors.indexOf(error);
        if (i != -1 && i < MAX_ERRORS_TO_SHOW) {
          fireIntervalAdded(this, i, i);
        }
      }
    }

    @Override
    public void removeError(@NotNull ConfigurationError error) {
      final int i = myAllErrors.indexOf(error);
      myIgnoredErrors.remove(error);
      myNotIgnoredErrors.remove(error);
      if (i != -1 && i < MAX_ERRORS_TO_SHOW) {
        fireIntervalRemoved(this, i, i);
      }
    }

    public List<ConfigurationError> getErrors() {
      return myNotIgnoredErrors;
    }

    public List<ConfigurationError> getIgnoredErrors() {
      return myIgnoredErrors;
    }

    @Override
    public void dispose() {
      if (myConnection != null) {
        myConnection.disconnect();
        myConnection = null;
      }
    }

    public void update(final ConfigurationError error) {
      final int i0 = myAllErrors.indexOf(error);
      if (error.isIgnored()) {
        if (myNotIgnoredErrors.remove(error)) {
          myIgnoredErrors.add(0, error);
        }
      }
      else {
        if (myIgnoredErrors.remove(error)) {
          myNotIgnoredErrors.add(0, error);
        }
      }
      final int i1 = myAllErrors.indexOf(error);
      if (i0 == i1 && i0 != -1) {
        if (i0 < MAX_ERRORS_TO_SHOW) {
          fireContentsChanged(this, i0, i0);
        }
      }
      else {
        if (i0 != -1 && i0 < MAX_ERRORS_TO_SHOW) {
          fireIntervalRemoved(this, i0, i0);
        }
        if (i1 != -1 && i1 < MAX_ERRORS_TO_SHOW) {
          fireIntervalAdded(this, i1, i1);
        }
      }
    }
  }
}

