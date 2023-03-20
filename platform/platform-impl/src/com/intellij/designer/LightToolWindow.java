// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.designer;

import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.actions.ToolWindowViewModeAction;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.actionSystem.impl.ActionManagerImpl;
import com.intellij.openapi.actionSystem.impl.MenuItemPresentationFactory;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ThreeComponentsSplitter;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.impl.AnchoredButton;
import com.intellij.toolWindow.StripeButtonUi;
import com.intellij.ui.*;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.ui.tabs.TabsUtil;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public final class LightToolWindow extends JPanel {
  static final String LEFT_MIN_KEY = "left";
  static final String RIGHT_MIN_KEY = "right";
  static final int MINIMIZE_WIDTH = 25;
  private static final String IGNORE_WIDTH_KEY = "ignore_width";

  private final LightToolWindowContent myContent;
  private final JComponent myFocusedComponent;
  private final ThreeComponentsSplitter myContentSplitter;
  private ToolWindowAnchor myAnchor;
  private final Project myProject;
  private final LightToolWindowManager myManager;
  private final PropertiesComponent myPropertiesComponent;
  private boolean myShowContent;
  private final String myShowStateKey;
  private int myCurrentWidth;
  private final String myWidthKey;
  private final JPanel myMinimizeComponent;
  private final AnchoredButton myMinimizeButton;

  private final ComponentListener myWidthListener = new ComponentAdapter() {
    @Override
    public void componentResized(ComponentEvent e) {
      int width = isLeft() ? myContentSplitter.getFirstSize() : myContentSplitter.getLastSize();
      if (width > 0 && width != myCurrentWidth && myContentSplitter.getInnerComponent().getClientProperty(IGNORE_WIDTH_KEY) == null) {
        myCurrentWidth = width;
        myPropertiesComponent.setValue(myWidthKey, Integer.toString(width));
      }
    }
  };

  public LightToolWindow(@NotNull LightToolWindowContent content,
                         @NotNull @Nls(capitalization = Nls.Capitalization.Title) String title,
                         @NotNull Icon icon,
                         @NotNull JComponent component,
                         @NotNull JComponent focusedComponent,
                         @NotNull ThreeComponentsSplitter contentSplitter,
                         @Nullable ToolWindowAnchor anchor,
                         @NotNull LightToolWindowManager manager,
                         @NotNull Project project,
                         @NotNull String key,
                         int defaultWidth,
                         @Nullable List<AnAction> actions) {
    super(new BorderLayout());
    myContent = content;
    myFocusedComponent = focusedComponent;
    myContentSplitter = contentSplitter;
    myAnchor = anchor;
    myProject = project;
    myManager = manager;
    myPropertiesComponent = PropertiesComponent.getInstance(myProject);

    myShowStateKey = LightToolWindowManager.EDITOR_MODE + key + ".SHOW";
    myWidthKey = LightToolWindowManager.EDITOR_MODE + key + ".WIDTH";

    HeaderPanel header = new HeaderPanel();
    header.setLayout(new BorderLayout());
    add(header, BorderLayout.NORTH);

    JLabel titleLabel = new JLabel(title);
    titleLabel.setBorder(JBUI.Borders.empty(2, 5, 2, 10));
    titleLabel.setFont(UIUtil.getLabelFont(UIUtil.FontSize.SMALL));
    header.add(titleLabel, BorderLayout.CENTER);

    JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 0));
    actionPanel.setBorder(JBUI.Borders.empty(3, 0, 2, 0));
    actionPanel.setOpaque(false);
    header.add(actionPanel, BorderLayout.EAST);

    if (actions != null) {
      for (AnAction action : actions) {
        addAction(actionPanel, action);
      }

      actionPanel.add(new JLabel(AllIcons.General.Divider));
    }

    addAction(actionPanel, new GearAction());
    addAction(actionPanel, new HideAction());

    JPanel contentWrapper = new JPanel(new BorderLayout());
    contentWrapper.setBorder(IdeBorderFactory.createBorder(SideBorder.TOP));
    contentWrapper.add(component, BorderLayout.CENTER);

    add(contentWrapper, BorderLayout.CENTER);

    addMouseListener(new MouseAdapter() {
      @Override
      public void mouseReleased(final MouseEvent e) {
        IdeFocusManager.getInstance(myProject).requestFocus(myFocusedComponent, true);
      }
    });

    addMouseListener(new PopupHandler() {
      @Override
      public void invokePopup(Component component, int x, int y) {
        showGearPopup(component, x, y);
      }
    });

    myMinimizeButton = new AnchoredButton(title, icon) {
      @Override
      public void updateUI() {
        setUI(new StripeButtonUi());
        setFont(UIUtil.getLabelFont(UIUtil.FontSize.SMALL));
      }

      @Override
      public int getMnemonic2() {
        return 0;
      }

      @Override
      public ToolWindowAnchor getAnchor() {
        return myAnchor;
      }
    };
    myMinimizeButton.addActionListener(e -> {
      myMinimizeButton.setSelected(false);
      updateContent(true, true);
    });
    myMinimizeButton.setBorder(BorderFactory.createEmptyBorder(5, 5, 0, 5));
    myMinimizeButton.setFocusable(false);

    myMinimizeButton.setRolloverEnabled(true);
    myMinimizeButton.setOpaque(false);

    myMinimizeComponent = new JPanel() {
      @Override
      public void doLayout() {
        Dimension size = myMinimizeButton.getPreferredSize();
        if (myAnchor == ToolWindowAnchor.BOTTOM) {
          myMinimizeButton.setBounds(0, 1, size.width, MINIMIZE_WIDTH);
        }
        else {
          myMinimizeButton.setBounds(0, 0, getWidth(), size.height);
        }
      }
    };
    myMinimizeComponent.add(myMinimizeButton);

    configureBorder();
    configureWidth(defaultWidth);
    updateContent(myPropertiesComponent.getBoolean(myShowStateKey, true), false);
  }

  private void configureBorder() {
    int borderStyle;
    if (myAnchor == ToolWindowAnchor.LEFT) {
      borderStyle = SideBorder.RIGHT;
    }
    else if (myAnchor == ToolWindowAnchor.RIGHT) {
      borderStyle = SideBorder.LEFT;
    }
    else if (myAnchor == ToolWindowAnchor.BOTTOM) {
      borderStyle = SideBorder.TOP;
    }
    else {
      return;
    }

    setBorder(IdeBorderFactory.createBorder(borderStyle));
    myMinimizeComponent.setBorder(IdeBorderFactory.createBorder(borderStyle));
  }

  private void configureWidth(int defaultWidth) {
    myCurrentWidth = myPropertiesComponent.getInt(myWidthKey, defaultWidth);
    updateWidth();
    myContentSplitter.getInnerComponent().addComponentListener(myWidthListener);
  }

  private void updateWidth() {
    if (isLeft()) {
      myContentSplitter.setFirstSize(myCurrentWidth);
    }
    else {
      myContentSplitter.setLastSize(myCurrentWidth);
    }
  }

  void updateAnchor(ToolWindowAnchor newAnchor) {
    JComponent minimizeParent = myContentSplitter.getInnerComponent();
    minimizeParent.putClientProperty(IGNORE_WIDTH_KEY, Boolean.TRUE);

    if (myShowContent) {
      Object oldWindow = isLeft() ? myContentSplitter.getFirstComponent() : myContentSplitter.getLastComponent();
      if (oldWindow == this) {
        setContentComponent(null);
      }
    }
    else {
      String key = getMinKey();
      if (minimizeParent.getClientProperty(key) == myMinimizeComponent) {
        minimizeParent.putClientProperty(key, null);
      }
      minimizeParent.putClientProperty(isLeft() ? RIGHT_MIN_KEY : LEFT_MIN_KEY, myMinimizeComponent);
      minimizeParent.revalidate();
    }

    myAnchor = newAnchor;
    configureBorder();
    updateWidth();

    if (myShowContent) {
      setContentComponent(this);
    }

    minimizeParent.putClientProperty(IGNORE_WIDTH_KEY, null);
  }

  private void updateContent(boolean show, boolean flag) {
    myShowContent = show;

    String key = getMinKey();

    JComponent minimizeParent = myContentSplitter.getInnerComponent();

    if (show) {
      minimizeParent.putClientProperty(key, null);
      minimizeParent.remove(myMinimizeComponent);
    }

    setContentComponent(show ? this : null);

    if (!show) {
      minimizeParent.putClientProperty(key, myMinimizeComponent);
      minimizeParent.add(myMinimizeComponent);
    }

    minimizeParent.revalidate();

    if (flag) {
      myPropertiesComponent.setValue(myShowStateKey, Boolean.toString(show));
    }
  }

  private void setContentComponent(JComponent component) {
    if (isLeft()) {
      myContentSplitter.setFirstComponent(component);
    }
    else {
      myContentSplitter.setLastComponent(component);
    }
  }

  public void dispose() {
    JComponent minimizeParent = myContentSplitter.getInnerComponent();
    minimizeParent.removeComponentListener(myWidthListener);

    setContentComponent(null);
    myContent.dispose();

    if (!myShowContent) {
      minimizeParent.putClientProperty(getMinKey(), null);
      minimizeParent.remove(myMinimizeComponent);
      minimizeParent.revalidate();
    }
  }

  private String getMinKey() {
    return isLeft() ? LEFT_MIN_KEY : RIGHT_MIN_KEY;
  }

  public Object getContent() {
    return myContent;
  }

  private boolean isLeft() {
    return myAnchor == ToolWindowAnchor.LEFT;
  }

  private boolean isActive() {
    IdeFocusManager fm = IdeFocusManager.getInstance(myProject);
    Component component = fm.getFocusedDescendantFor(this);
    if (component != null) {
      return true;
    }
    Component owner = fm.getLastFocusedFor(WindowManager.getInstance().getFrame(myProject));
    return owner != null && SwingUtilities.isDescendingFrom(owner, this);
  }

  private void addAction(JPanel actionPanel, AnAction action) {
    actionPanel.add(new ActionButton(action));
  }

  private DefaultActionGroup createGearPopupGroup() {
    DefaultActionGroup group = new DefaultActionGroup();

    group.add(myManager.createGearActions());
    if (myManager.getAnchor() == null) {
      group.addSeparator();
      DefaultActionGroup viewModeGroup = DefaultActionGroup.createPopupGroup(() -> ActionsBundle.groupText("TW.ViewModeGroup"));
      for (ToolWindowViewModeAction.ViewMode viewMode : ToolWindowViewModeAction.ViewMode.values()) {
        viewModeGroup.add(new MyViewModeAction(viewMode));
      }
      group.add(viewModeGroup);
    }
    return group;
  }

  private void showGearPopup(Component component, int x, int y) {
    ActionPopupMenu popupMenu =
      ((ActionManagerImpl)ActionManager.getInstance())
        .createActionPopupMenu(ActionPlaces.TOOLWINDOW_POPUP, createGearPopupGroup(), new MenuItemPresentationFactory());
    popupMenu.getComponent().show(component, x, y);
  }

  private class GearAction extends AnAction {
    GearAction() {
      Presentation presentation = getTemplatePresentation();
      presentation.setIcon(AllIcons.General.GearPlain);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      int x = 0;
      int y = 0;
      InputEvent inputEvent = e.getInputEvent();
      if (inputEvent instanceof MouseEvent) {
        x = ((MouseEvent)inputEvent).getX();
        y = ((MouseEvent)inputEvent).getY();
      }

      showGearPopup(inputEvent.getComponent(), x, y);
    }
  }

  private class HideAction extends AnAction {
    HideAction() {
      Presentation presentation = getTemplatePresentation();
      presentation.setText(UIBundle.messagePointer("tool.window.hide.action.name"));
      presentation.setIcon(AllIcons.General.HideToolWindow);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      updateContent(false, true);
    }
  }

  private final class MyViewModeAction extends ToolWindowViewModeAction {
    private MyViewModeAction(@NotNull ViewMode mode) {
      super(mode);
      ActionUtil.copyFrom(this, mode.getActionID());
    }

    @Nullable
    @Override
    protected ToolWindow getToolWindow(AnActionEvent e) {
      return myManager.getToolWindow();
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      super.setSelected(e, state);
      myManager.setEditorMode(null);
    }
  }

  private class ActionButton extends Wrapper implements ActionListener {
    private final AnAction myAction;

    ActionButton(AnAction action) {
      myAction = action;

      Presentation presentation = action.getTemplatePresentation();
      InplaceButton button = new InplaceButton(KeymapUtil.createTooltipText(presentation.getText(), action), EmptyIcon.ICON_16, this) {
        @Override
        public boolean isActive() {
          return LightToolWindow.this.isActive();
        }
      };
      button.setHoveringEnabled(!SystemInfo.isMac);
      setContent(button);

      Icon icon = presentation.getIcon();
      Icon hoveredIcon = presentation.getHoveredIcon();
      button.setIcons(icon, icon, hoveredIcon == null ? icon : hoveredIcon);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      InputEvent inputEvent = e.getSource() instanceof InputEvent ? (InputEvent)e.getSource() : null;
      DataContext dataContext = DataManager.getInstance().getDataContext(this);
      myAction.actionPerformed(AnActionEvent.createFromAnAction(myAction, inputEvent, ActionPlaces.TOOLWINDOW_TITLE, dataContext));
    }
  }

  private static class HeaderPanel extends JPanel {
    @Override
    public Dimension getPreferredSize() {
      Dimension size = super.getPreferredSize();
      return new Dimension(size.width, TabsUtil.getTabsHeight());
    }

    @Override
    public Dimension getMinimumSize() {
      Dimension size = super.getMinimumSize();
      return new Dimension(size.width, TabsUtil.getTabsHeight());
    }
  }
}