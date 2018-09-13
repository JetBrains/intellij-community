// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.icons.AllIcons;
import com.intellij.ide.util.ElementsChooser;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.JBColor;
import com.intellij.ui.ScrollingUtil;
import com.intellij.ui.WindowMoveListener;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.components.fields.ExtendableTextField;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.components.BorderLayoutPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public abstract class BigPopupUI extends BorderLayoutPanel implements Disposable {
  protected final Project myProject;
  protected JBTextField mySearchField;
  protected JPanel suggestionsPanel;
  protected JBList<Object> myResultsList;
  protected JBPopup myHint;
  protected Runnable searchFinishedHandler = () -> {
  };
  protected final List<ViewTypeListener> myViewTypeListeners = new ArrayList<>();
  protected ViewType myViewType = ViewType.SHORT;
  protected JLabel myHintLabel;

  public BigPopupUI(Project project) {
    myProject = project;
  }

  @NotNull
  public abstract JBList<Object> createList();

  @NotNull
  protected abstract ListCellRenderer<Object> createCellRenderer();

  @NotNull
  protected abstract JPanel createTopLeftPanel();

  @NotNull
  protected abstract JPanel createSettingsPanel();

  @NotNull
  protected abstract String getInitialHint();

  protected void installScrollingActions() {
    ScrollingUtil.installActions(myResultsList, getSearchField());
  }

  protected static class SearchField extends ExtendableTextField {
    public SearchField() {
      ExtendableTextField.Extension leftExtension = getLeftExtension();
      ExtendableTextField.Extension rightExtension = getRightExtension();
      if (leftExtension != null) {
        addExtension(leftExtension);
      }
      if (rightExtension != null) {
        addExtension(rightExtension);
      }

      Insets insets = JBUI.CurrentTheme.BigPopup.searchFieldInsets();
      Border empty = JBUI.Borders.empty(insets.top, insets.left, insets.bottom, insets.right);
      Border topLine = JBUI.Borders.customLine(JBUI.CurrentTheme.BigPopup.searchFieldBorderColor(), 1, 0, 0, 0);
      setBorder(JBUI.Borders.merge(empty, topLine, true));
      setBackground(JBUI.CurrentTheme.BigPopup.searchFieldBackground());
      setFocusTraversalKeysEnabled(false);

      if (Registry.is("new.search.everywhere.use.editor.font")) {
        Font editorFont = EditorUtil.getEditorFont();
        setFont(editorFont);
      }

      int fontDelta = Registry.intValue("new.search.everywhere.font.size.delta");
      if (fontDelta != 0) {
        Font font = getFont();
        font = font.deriveFont((float)fontDelta + font.getSize());
        setFont(font);
      }
    }

    @Override
    public Dimension getPreferredSize() {
      Dimension size = super.getPreferredSize();
      size.height = Integer.max(JBUI.scale(29), size.height);
      return size;
    }

    @Nullable
    protected ExtendableTextField.Extension getRightExtension() {
      return null;
    }

    @Nullable
    protected ExtendableTextField.Extension getLeftExtension() {
      return null;
    }
  }

  @NotNull
  protected ExtendableTextField createSearchField() {
    return new SearchField();
  }

  public void init() {
    withBackground(JBUI.CurrentTheme.BigPopup.dialogBackground());

    myResultsList = createList();

    JPanel topLeftPanel = createTopLeftPanel();
    JPanel settingsPanel = createSettingsPanel();
    mySearchField = createSearchField();
    suggestionsPanel = createSuggestionsPanel();

    myResultsList.setFocusable(false);
    myResultsList.setCellRenderer(createCellRenderer());

    if (Registry.is("new.search.everywhere.use.editor.font")) {
      Font editorFont = EditorUtil.getEditorFont();
      myResultsList.setFont(editorFont);
    }

    installScrollingActions();

    JPanel topPanel = new JPanel(new BorderLayout());
    topPanel.setOpaque(false);
    topPanel.add(topLeftPanel, BorderLayout.WEST);
    topPanel.add(settingsPanel, BorderLayout.EAST);
    topPanel.add(mySearchField, BorderLayout.SOUTH);

    WindowMoveListener moveListener = new WindowMoveListener(this);
    topPanel.addMouseListener(moveListener);
    topPanel.addMouseMotionListener(moveListener);

    addToTop(topPanel);
    addToCenter(suggestionsPanel);
  }

  protected void addListDataListener(@NotNull AbstractListModel<Object> model) {
    model.addListDataListener(new ListDataListener() {
      @Override
      public void intervalAdded(ListDataEvent e) {
        updateViewType(ViewType.FULL);
      }

      @Override
      public void intervalRemoved(ListDataEvent e) {
        if (myResultsList.isEmpty() && getSearchPattern().isEmpty()) {
          updateViewType(ViewType.SHORT);
        }
      }

      @Override
      public void contentsChanged(ListDataEvent e) {
        updateViewType(myResultsList.isEmpty() && getSearchPattern().isEmpty() ? ViewType.SHORT : ViewType.FULL);
      }
    });
  }

  @NotNull
  protected String getSearchPattern() {
    return Optional.ofNullable(mySearchField)
      .map(JTextComponent::getText)
      .orElse("");
  }

  protected void updateViewType(@NotNull ViewType viewType) {
    if (myViewType != viewType) {
      myViewType = viewType;
      myViewTypeListeners.forEach(listener -> listener.suggestionsShown(viewType));
    }
  }

  private JPanel createSuggestionsPanel() {
    JPanel pnl = new JPanel(new BorderLayout());
    pnl.setOpaque(false);
    pnl.setBorder(JBUI.Borders.customLine(JBUI.CurrentTheme.BigPopup.searchFieldBorderColor(), 1, 0, 0, 0));

    JScrollPane resultsScroll = new JBScrollPane(myResultsList);
    resultsScroll.setBorder(null);
    resultsScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

    resultsScroll.setPreferredSize(JBUI.size(670, JBUI.CurrentTheme.BigPopup.maxListHeight()));
    pnl.add(resultsScroll, BorderLayout.CENTER);

    myHintLabel = createHint();
    pnl.add(myHintLabel, BorderLayout.SOUTH);

    return pnl;
  }

  @NotNull
  private JLabel createHint() {
    String hint = getInitialHint();
    JLabel hintLabel = HintUtil.createAdComponent(hint, JBUI.Borders.emptyLeft(8), SwingConstants.LEFT);
    hintLabel.setOpaque(false);
    hintLabel.setForeground(JBColor.GRAY);
    Dimension size = hintLabel.getPreferredSize();
    size.height = JBUI.scale(17);
    hintLabel.setPreferredSize(size);
    return hintLabel;
  }

  @NotNull
  public JTextField getSearchField() {
    return mySearchField;
  }

  @Override
  public Dimension getMinimumSize() {
    return calcPrefSize(ViewType.SHORT);
  }

  @Override
  public Dimension getPreferredSize() {
    return calcPrefSize(myViewType);
  }

  private Dimension calcPrefSize(ViewType viewType) {
    Dimension size = super.getPreferredSize();
    if (viewType == ViewType.SHORT) {
      size.height -= suggestionsPanel.getPreferredSize().height;
    }
    return size;
  }

  public void setSearchFinishedHandler(@NotNull Runnable searchFinishedHandler) {
    this.searchFinishedHandler = searchFinishedHandler;
  }

  public ViewType getViewType() {
    return myViewType;
  }

  public enum ViewType {FULL, SHORT}

  public interface ViewTypeListener {
    void suggestionsShown(@NotNull ViewType viewType);
  }

  public void addViewTypeListener(ViewTypeListener listener) {
    myViewTypeListeners.add(listener);
  }

  public void removeViewTypeListener(ViewTypeListener listener) {
    myViewTypeListeners.remove(listener);
  }

  protected abstract class ShowFilterAction extends ToggleAction implements DumbAware {
    private JBPopup myFilterPopup;

    public ShowFilterAction() {
      super("Filter", "Filter files by type", AllIcons.General.Filter);
    }

    @Override
    public boolean isSelected(@NotNull final AnActionEvent e) {
      return myFilterPopup != null && !myFilterPopup.isDisposed();
    }

    @Override
    public void setSelected(@NotNull final AnActionEvent e, final boolean state) {
      if (state) {
        showPopup(e.getInputEvent().getComponent());
      }
      else {
        if (myFilterPopup != null && !myFilterPopup.isDisposed()) {
          myFilterPopup.cancel();
        }
      }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      Icon icon = getTemplatePresentation().getIcon();
      e.getPresentation().setIcon(isActive() ? ExecutionUtil.getLiveIndicator(icon) : icon);
      e.getPresentation().setEnabled(isEnabled());
      e.getPresentation().putClientProperty(SELECTED_PROPERTY, isSelected(e));
    }

    protected abstract boolean isEnabled();

    protected abstract boolean isActive();

    private void showPopup(Component anchor) {
      if (myFilterPopup != null) {
        return;
      }
      JBPopupListener popupCloseListener = new JBPopupListener() {
        @Override
        public void onClosed(@NotNull LightweightWindowEvent event) {
          myFilterPopup = null;
        }
      };
      myFilterPopup = JBPopupFactory.getInstance()
        .createComponentPopupBuilder(createFilterPanel(), null)
        .setModalContext(false)
        .setFocusable(false)
        .setResizable(true)
        .setCancelOnClickOutside(false)
        .setMinSize(new Dimension(200, 200))
        .setDimensionServiceKey(myProject, "Search_Everywhere_Filter_Popup", false)
        .addListener(popupCloseListener)
        .createPopup();
      Disposer.register(BigPopupUI.this, myFilterPopup);
      myFilterPopup.showUnderneathOf(anchor);
    }

    private JComponent createFilterPanel() {
      ElementsChooser<?> chooser = createChooser();

      JPanel panel = new JPanel();
      panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
      panel.add(chooser);
      JPanel buttons = new JPanel();
      JButton all = new JButton("All");
      all.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(final ActionEvent e) {
          chooser.setAllElementsMarked(true);
        }
      });
      buttons.add(all);
      JButton none = new JButton("None");
      none.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(final ActionEvent e) {
          chooser.setAllElementsMarked(false);
        }
      });
      buttons.add(none);
      JButton invert = new JButton("Invert");
      invert.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(final ActionEvent e) {
          chooser.invertSelection();
        }
      });
      buttons.add(invert);
      panel.add(buttons);
      return panel;
    }

    protected abstract ElementsChooser<?> createChooser();
  }
}