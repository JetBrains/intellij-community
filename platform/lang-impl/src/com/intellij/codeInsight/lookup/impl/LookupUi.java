// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.lookup.impl;

import com.intellij.application.options.CodeCompletionConfigurable;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.completion.ShowHideIntentionIconLookupAction;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.ui.UISettings;
import com.intellij.idea.ActionsBundle;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.ComponentUtil;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.ScreenUtil;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBLayeredPane;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.Alarm;
import com.intellij.util.PlatformIcons;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.ui.AbstractLayoutManager;
import com.intellij.util.ui.Advertiser;
import com.intellij.util.ui.AsyncProcessIcon;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.MouseEvent;

class LookupUi {
  private static final Logger LOG = Logger.getInstance(LookupUi.class);

  @NotNull
  private final LookupImpl myLookup;
  private final Advertiser myAdvertiser;
  private final JBList myList;
  private final ModalityState myModalityState;
  private final Alarm myHintAlarm = new Alarm();
  private final JScrollPane myScrollPane;
  private final AsyncProcessIcon myProcessIcon = new AsyncProcessIcon("Completion progress");
  private final ActionButton myMenuButton;
  private final ActionButton myHintButton;
  private final JComponent myBottomPanel;

  private int myMaximumHeight = Integer.MAX_VALUE;
  private Boolean myPositionedAbove = null;

  LookupUi(@NotNull LookupImpl lookup, Advertiser advertiser, JBList list, boolean showBottomPanel) {
    myLookup = lookup;
    myAdvertiser = advertiser;
    myList = list;

    myProcessIcon.setVisible(false);
    myLookup.resort(false);

    MenuAction menuAction = new MenuAction();
    menuAction.add(new ChangeSortingAction());
    menuAction.add(new DelegatedAction(ActionManager.getInstance().getAction(IdeActions.ACTION_QUICK_JAVADOC)){
      @Override
      public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setVisible(!CodeInsightSettings.getInstance().AUTO_POPUP_JAVADOC_INFO);
      }

      @Override
      public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
      }
    });
    menuAction.add(new DelegatedAction(ActionManager.getInstance().getAction(IdeActions.ACTION_QUICK_IMPLEMENTATIONS)));
    menuAction.addSeparator();
    menuAction.add(new ShowCompletionSettingsAction());

    Presentation presentation = new Presentation();
    presentation.setIcon(AllIcons.Actions.More);
    presentation.putClientProperty(ActionButton.HIDE_DROPDOWN_ICON, Boolean.TRUE);

    myMenuButton = new ActionButton(menuAction, presentation, ActionPlaces.EDITOR_POPUP, ActionToolbar.NAVBAR_MINIMUM_BUTTON_SIZE);
    DataManager.registerDataProvider(myMenuButton, dataId -> {
      if (CommonDataKeys.PROJECT.is(dataId)) {
        return myLookup.getProject();
      }
      if (CommonDataKeys.EDITOR.is(dataId)) {
        return myLookup.getEditor();
      }
      return null;
    });

    AnAction hintAction = new HintAction();
    myHintButton = new ActionButton(hintAction, hintAction.getTemplatePresentation().clone(),
                                    ActionPlaces.EDITOR_POPUP, ActionToolbar.NAVBAR_MINIMUM_BUTTON_SIZE);
    myHintButton.setVisible(false);

    myBottomPanel = new JPanel(new LookupBottomLayout());
    myBottomPanel.add(myAdvertiser.getAdComponent());
    myBottomPanel.add(myProcessIcon);
    myBottomPanel.add(myHintButton);
    myBottomPanel.add(myMenuButton);

    LookupLayeredPane layeredPane = new LookupLayeredPane();
    if (showBottomPanel) {
      layeredPane.mainPanel.add(myBottomPanel, BorderLayout.SOUTH);
    }

    myScrollPane = ScrollPaneFactory.createScrollPane(lookup.getList(), true);
    myScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    ComponentUtil.putClientProperty(myScrollPane.getVerticalScrollBar(), JBScrollPane.IGNORE_SCROLLBAR_IN_INSETS, true);
    if (ExperimentalUI.isNewUI()) {
      Insets bodyInsets = LookupCellRenderer.bodyInsets();
      //noinspection UseDPIAwareBorders
      myScrollPane.setBorder(new EmptyBorder(bodyInsets.top, 0, showBottomPanel ? 0 : bodyInsets.bottom, 0));
      myBottomPanel.setBackground(JBUI.CurrentTheme.CompletionPopup.Advertiser.background());
      myBottomPanel.setBorder(JBUI.CurrentTheme.CompletionPopup.Advertiser.border());
    } else {
      myBottomPanel.setOpaque(false);
    }

    lookup.getComponent().add(layeredPane, BorderLayout.CENTER);

    layeredPane.mainPanel.add(myScrollPane, BorderLayout.CENTER);

    myModalityState = ModalityState.stateForComponent(lookup.getTopLevelEditor().getComponent());

    addListeners();

    Disposer.register(lookup, myProcessIcon);
    Disposer.register(lookup, myHintAlarm);
  }

  private void addListeners() {
    myList.addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        if (myLookup.isLookupDisposed()) return;

        myHintAlarm.cancelAllRequests();
        updateHint();
      }
    });

    myScrollPane.getVerticalScrollBar().addAdjustmentListener(e -> {
      if (myLookup.myUpdating || myLookup.isLookupDisposed()) return;
      myLookup.myCellRenderer.scheduleUpdateLookupWidthFromVisibleItems();
    });
  }

  private void updateHint() {
    myLookup.checkValid();
    if (myHintButton.isVisible()) {
      myHintButton.setVisible(false);
    }

    LookupElement item = myLookup.getCurrentItem();
    if (item != null && item.isValid()) {
      ReadAction.nonBlocking(() -> myLookup.getActionsFor(item))
        .expireWhen(() -> !item.isValid() || myHintAlarm.isDisposed())
        .finishOnUiThread(ModalityState.NON_MODAL, actions -> {
          if (!actions.isEmpty()) {
            myHintAlarm.addRequest(() -> {
              if (ShowHideIntentionIconLookupAction.shouldShowLookupHint() &&
                  !((CompletionExtender)myList.getExpandableItemsHandler()).isShowing() &&
                  !myProcessIcon.isVisible()) {
                myHintButton.setVisible(true);
              }
            }, 500, myModalityState);
          }
        })
        .submit(AppExecutorUtil.getAppExecutorService());
    }
  }

  void setCalculating(boolean calculating) {
    Runnable iconUpdater = () -> {
      if (calculating && myHintButton.isVisible()) {
        myHintButton.setVisible(false);
      }
      myProcessIcon.setVisible(calculating);

      ApplicationManager.getApplication().invokeLater(() -> {
        if (!calculating && !myLookup.isLookupDisposed()) {
          updateHint();
        }
      }, myModalityState);
    };

    if (calculating) {
      myProcessIcon.resume();
    } else {
      myProcessIcon.suspend();
    }
    new Alarm(myLookup).addRequest(iconUpdater, 100, myModalityState);
  }

  void refreshUi(boolean selectionVisible, boolean itemsChanged, boolean reused, boolean onExplicitAction) {
    Editor editor = myLookup.getTopLevelEditor();
    if (editor.getComponent().getRootPane() == null || editor instanceof EditorWindow && !((EditorWindow)editor).isValid()) {
      return;
    }

    if (myLookup.myResizePending || itemsChanged) {
      myMaximumHeight = Integer.MAX_VALUE;
    }
    Rectangle rectangle = calculatePosition();
    myMaximumHeight = rectangle.height;

    if (myLookup.myResizePending || itemsChanged) {
      myLookup.myResizePending = false;
      myLookup.pack();
      rectangle = calculatePosition();
    }
    HintManagerImpl.updateLocation(myLookup, editor, rectangle.getLocation());

    if (reused || selectionVisible || onExplicitAction) {
      myLookup.ensureSelectionVisible(false);
    }
  }

  boolean isPositionedAboveCaret() {
    return myPositionedAbove != null && myPositionedAbove.booleanValue();
  }

  // in layered pane coordinate system.
  Rectangle calculatePosition() {
    final JComponent lookupComponent = myLookup.getComponent();
    Dimension dim = lookupComponent.getPreferredSize();
    int lookupStart = myLookup.getLookupStart();
    Editor editor = myLookup.getTopLevelEditor();
    if (lookupStart < 0 || lookupStart > editor.getDocument().getTextLength()) {
      LOG.error(lookupStart + "; offset=" + editor.getCaretModel().getOffset() + "; element=" +
                myLookup.getPsiElement());
    }

    LogicalPosition pos = editor.offsetToLogicalPosition(lookupStart);
    Point location = editor.logicalPositionToXY(pos);
    location.y += editor.getLineHeight();
    location.x -= myLookup.myCellRenderer.getTextIndent();
    // extra check for other borders
    final Window window = ComponentUtil.getWindow(lookupComponent);
    if (window != null) {
      final Point point = SwingUtilities.convertPoint(lookupComponent, 0, 0, window);
      location.x -= point.x;
    }

    SwingUtilities.convertPointToScreen(location, editor.getContentComponent());
    final Rectangle screenRectangle = ScreenUtil.getScreenRectangle(editor.getContentComponent());

    if (!isPositionedAboveCaret()) {
      int shiftLow = screenRectangle.y + screenRectangle.height - (location.y + dim.height);
      myPositionedAbove = shiftLow < 0 && shiftLow < location.y - dim.height && location.y >= dim.height;
    }
    if (isPositionedAboveCaret()) {
      location.y -= dim.height + editor.getLineHeight();
    }

    if (!screenRectangle.contains(location)) {
      location = ScreenUtil.findNearestPointOnBorder(screenRectangle, location);
    }

    Rectangle candidate = new Rectangle(location, dim);
    ScreenUtil.cropRectangleToFitTheScreen(candidate);

    if (isPositionedAboveCaret()) {
      // need to crop as well at bottom if lookup overlaps current line
      Point caretLocation = editor.logicalPositionToXY(pos);
      SwingUtilities.convertPointToScreen(caretLocation, editor.getContentComponent());
      int offset = location.y + dim.height - caretLocation.y;
      if (offset > 0) {
        candidate.height -= offset;
      }
    }

    JRootPane rootPane = editor.getComponent().getRootPane();
    if (rootPane != null) {
      SwingUtilities.convertPointFromScreen(location, rootPane.getLayeredPane());
    }
    else {
      LOG.error("editor.disposed=" + editor.isDisposed() + "; lookup.disposed=" + myLookup.isLookupDisposed() + "; editorShowing=" + editor.getContentComponent().isShowing());
    }

    myMaximumHeight = candidate.height;
    return new Rectangle(location.x, location.y, dim.width, candidate.height);
  }

  private static class ShowCompletionSettingsAction extends AnAction implements DumbAware {
    ShowCompletionSettingsAction() {
      super(LangBundle.message("action.code.completion.settings.text"), null, AllIcons.General.Settings);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      ShowSettingsUtil.getInstance().showSettingsDialog(getEventProject(e), CodeCompletionConfigurable.class);
    }
  }

  private final class LookupLayeredPane extends JBLayeredPane {
    final JPanel mainPanel = new JPanel(new BorderLayout());

    private LookupLayeredPane() {
      mainPanel.setBackground(LookupCellRenderer.BACKGROUND_COLOR);
      add(mainPanel, 0, 0);

      setLayout(new AbstractLayoutManager() {
        @Override
        public Dimension preferredLayoutSize(@Nullable Container parent) {
          int maxCellWidth = myLookup.myCellRenderer.getLookupTextWidth() + myLookup.myCellRenderer.getTextIndent();
          int scrollBarWidth = myScrollPane.getVerticalScrollBar().getWidth();
          int listWidth = Math.min(scrollBarWidth + maxCellWidth, UISettings.getInstance().getMaxLookupWidth());

          Dimension bottomPanelSize = myBottomPanel.getPreferredSize();

          int panelHeight = myScrollPane.getPreferredSize().height + bottomPanelSize.height;
          int width = Math.max(listWidth, bottomPanelSize.width);
          width = Math.min(width, Registry.intValue("ide.completion.max.width"));
          int height = Math.min(panelHeight, myMaximumHeight);

          return new Dimension(width, height);
        }

        @Override
        public void layoutContainer(Container parent) {
          Dimension size = getSize();
          mainPanel.setSize(size);
          mainPanel.validate();

          if (IdeEventQueue.getInstance().getTrueCurrentEvent().getID() == MouseEvent.MOUSE_DRAGGED) {
            Dimension preferredSize = preferredLayoutSize(null);
            if (preferredSize.width != size.width) {
              UISettings.getInstance().setMaxLookupWidth(Math.max(500, size.width));
            }

            int listHeight = myList.getLastVisibleIndex() - myList.getFirstVisibleIndex() + 1;
            if (listHeight != myList.getModel().getSize() && listHeight != myList.getVisibleRowCount() && preferredSize.height != size.height) {
              UISettings.getInstance().setMaxLookupListHeight(Math.max(5, listHeight));
            }
          }

          myList.setFixedCellWidth(myScrollPane.getViewport().getWidth());
        }
      });
    }
  }

  private final class HintAction extends DumbAwareAction {
    private HintAction() {
      super(AllIcons.Actions.IntentionBulb);

      AnAction showIntentionAction = ActionManager.getInstance().getAction(IdeActions.ACTION_SHOW_INTENTION_ACTIONS);
      if (showIntentionAction != null) {
        copyShortcutFrom(showIntentionAction);
        getTemplatePresentation().setText(CodeInsightBundle.messagePointer("action.presentation.LookupUi.text"));
      }
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      myLookup.showElementActions(e.getInputEvent());
    }
  }

  private static final class MenuAction extends DefaultActionGroup implements HintManagerImpl.ActionToIgnore {
    private MenuAction() {
      setPopup(true);
    }
  }

  private final class ChangeSortingAction extends DumbAwareAction implements HintManagerImpl.ActionToIgnore {
    private ChangeSortingAction() {
      super(ActionsBundle.messagePointer("action.ChangeSortingAction.text"));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      UISettings settings = UISettings.getInstance();
      settings.setSortLookupElementsLexicographically(!settings.getSortLookupElementsLexicographically());
      myLookup.resort(false);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setIcon(UISettings.getInstance().getSortLookupElementsLexicographically() ? PlatformIcons.CHECK_ICON : null);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }
  }

  private static class DelegatedAction extends DumbAwareAction implements HintManagerImpl.ActionToIgnore {
    private final AnAction delegateAction;
    private DelegatedAction(AnAction action) {
      delegateAction = action;
      getTemplatePresentation().setText(delegateAction.getTemplateText(), true);
      copyShortcutFrom(delegateAction);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      delegateAction.actionPerformed(e);
    }
  }

  private class LookupBottomLayout implements LayoutManager {
    @Override
    public void addLayoutComponent(String name, Component comp) {}

    @Override
    public void removeLayoutComponent(Component comp) {}

    @Override
    public Dimension preferredLayoutSize(Container parent) {
      Insets insets = parent.getInsets();
      Dimension adSize = myAdvertiser.getAdComponent().getPreferredSize();
      Dimension hintButtonSize = myHintButton.getPreferredSize();
      Dimension menuButtonSize = myMenuButton.getPreferredSize();

      return new Dimension(adSize.width + hintButtonSize.width + menuButtonSize.width + insets.left + insets.right,
                           Math.max(adSize.height, menuButtonSize.height) + insets.top + insets.bottom);
    }

    @Override
    public Dimension minimumLayoutSize(Container parent) {
      Insets insets = parent.getInsets();
      Dimension adSize = myAdvertiser.getAdComponent().getMinimumSize();
      Dimension hintButtonSize = myHintButton.getMinimumSize();
      Dimension menuButtonSize = myMenuButton.getMinimumSize();

      return new Dimension(adSize.width + hintButtonSize.width + menuButtonSize.width + insets.left + insets.right,
                           Math.max(adSize.height, menuButtonSize.height)  + insets.top + insets.bottom);
    }

    @Override
    public void layoutContainer(Container parent) {
      Insets insets = parent.getInsets();
      Dimension size = parent.getSize();
      int innerHeight = size.height - insets.top - insets.bottom;

      Dimension menuButtonSize = myMenuButton.getPreferredSize();
      int x = size.width - menuButtonSize.width - insets.right;
      int y = (innerHeight - menuButtonSize.height) / 2;

      myMenuButton.setBounds(x, y + insets.top, menuButtonSize.width, menuButtonSize.height);

      Dimension myHintButtonSize = myHintButton.getPreferredSize();
      if (myHintButton.isVisible() && !myProcessIcon.isVisible()) {
        x -= myHintButtonSize.width;
        y = (innerHeight - myHintButtonSize.height) / 2;
        myHintButton.setBounds(x, y + insets.top, myHintButtonSize.width, myHintButtonSize.height);
      }
      else if (!myHintButton.isVisible() && myProcessIcon.isVisible()) {
        Dimension myProcessIconSize = myProcessIcon.getPreferredSize();
        x -= myProcessIconSize.width;
        y = (innerHeight - myProcessIconSize.height) / 2;
        myProcessIcon.setBounds(x, y + insets.top, myProcessIconSize.width, myProcessIconSize.height);
      }
      else if (!myHintButton.isVisible() && !myProcessIcon.isVisible()) {
        x -= myHintButtonSize.width;
      }
      else {
        throw new IllegalStateException("Can't show both process icon and hint button");
      }

      Dimension adSize = myAdvertiser.getAdComponent().getPreferredSize();
      y = (innerHeight - adSize.height) / 2;
      myAdvertiser.getAdComponent().setBounds(insets.left, y + insets.top, x - insets.left, adSize.height);
    }
  }
}
