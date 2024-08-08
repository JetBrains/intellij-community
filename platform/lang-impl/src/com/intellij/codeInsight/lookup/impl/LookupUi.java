// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.lookup.impl;

import com.intellij.application.options.CodeCompletionConfigurable;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.completion.ShowHideIntentionIconLookupAction;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupPositionStrategy;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.ui.UISettings;
import com.intellij.idea.ActionsBundle;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.Disposable;
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
import com.intellij.util.concurrency.EdtScheduler;
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

final class LookupUi {
  private static final Logger LOG = Logger.getInstance(LookupUi.class);

  private final @NotNull LookupImpl lookup;
  private final Advertiser myAdvertiser;
  private final JBList<?> myList;
  private final ModalityState modalityState;
  private final Alarm hintAlarm;
  private final JScrollPane myScrollPane;
  private final AsyncProcessIcon processIcon = new AsyncProcessIcon("Completion progress");
  private final ActionButton myMenuButton;
  private final ActionButton hintButton;
  private final @Nullable JComponent myBottomPanel;

  private int myMaximumHeight = Integer.MAX_VALUE;
  private Boolean myPositionedAbove = null;

  LookupUi(@NotNull LookupImpl lookup, Advertiser advertiser, JBList<?> list, boolean showBottomPanel) {
    hintAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, lookup);

    this.lookup = lookup;
    myAdvertiser = advertiser;
    myList = list;

    processIcon.setVisible(false);
    this.lookup.resort(false);

    MenuAction menuAction = new MenuAction();
    menuAction.add(new ChangeSortingAction());
    menuAction.add(new DelegatedAction(ActionManager.getInstance().getAction(IdeActions.ACTION_QUICK_JAVADOC)) {
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

    myMenuButton = new ActionButton(menuAction, null, ActionPlaces.EDITOR_POPUP, ActionToolbar.NAVBAR_MINIMUM_BUTTON_SIZE);
    JComponent menuButtonWrapper = UiDataProvider.wrapComponent(myMenuButton, sink -> {
      sink.set(CommonDataKeys.PROJECT, this.lookup.getProject());
      sink.set(CommonDataKeys.EDITOR, this.lookup.getEditor());
    });

    AnAction hintAction = new HintAction();
    hintButton = new ActionButton(hintAction, hintAction.getTemplatePresentation().clone(),
                                  ActionPlaces.EDITOR_POPUP, ActionToolbar.NAVBAR_MINIMUM_BUTTON_SIZE);
    hintButton.setVisible(false);

    LookupLayeredPane layeredPane = new LookupLayeredPane();

    if (showBottomPanel) {
      myBottomPanel = new JPanel(new LookupBottomLayout());
      myBottomPanel.add(myAdvertiser.getAdComponent());
      myBottomPanel.add(processIcon);
      myBottomPanel.add(hintButton);
      myBottomPanel.add(menuButtonWrapper);
      if (ExperimentalUI.isNewUI()) {
        myBottomPanel.setBackground(JBUI.CurrentTheme.CompletionPopup.Advertiser.background());
        myBottomPanel.setBorder(JBUI.CurrentTheme.CompletionPopup.Advertiser.border());
      }
      else {
        myBottomPanel.setOpaque(false);
      }
      layeredPane.mainPanel.add(myBottomPanel, BorderLayout.SOUTH);
    }
    else {
      myBottomPanel = null;
    }

    myScrollPane = ScrollPaneFactory.createScrollPane(lookup.getList(), true);
    myScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    myScrollPane.getVerticalScrollBar().putClientProperty(JBScrollPane.IGNORE_SCROLLBAR_IN_INSETS, true);
    if (ExperimentalUI.isNewUI()) {
      Insets bodyInsets = showBottomPanel ? LookupCellRenderer.bodyInsetsWithAdvertiser() : LookupCellRenderer.bodyInsets();
      //noinspection UseDPIAwareBorders
      myScrollPane.setBorder(new EmptyBorder(bodyInsets.top, 0, bodyInsets.bottom, 0));
    }

    lookup.getComponent().add(layeredPane, BorderLayout.CENTER);

    layeredPane.mainPanel.add(myScrollPane, BorderLayout.CENTER);

    modalityState = ModalityState.stateForComponent(lookup.getTopLevelEditor().getComponent());

    addListeners();

    Disposer.register(lookup, new Disposable() {
      @Override
      public void dispose() {
        processIcon.dispose();
      }
    });
  }

  private void addListeners() {
    myList.addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        if (!lookup.isLookupDisposed()) {
          hintAlarm.cancelAllRequests();
          updateHint();
        }
      }
    });

    myScrollPane.getVerticalScrollBar().addAdjustmentListener(e -> {
      if (lookup.myUpdating || lookup.isLookupDisposed()) return;
      lookup.cellRenderer.scheduleUpdateLookupWidthFromVisibleItems();
    });
  }

  private void updateHint() {
    lookup.checkValid();
    if (hintButton.isVisible()) {
      hintButton.setVisible(false);
    }

    LookupElement item = lookup.getCurrentItem();
    if (item != null && item.isValid()) {
      ReadAction.nonBlocking(() -> lookup.getActionsFor(item))
        .expireWhen(() -> !item.isValid() || hintAlarm.isDisposed())
        .finishOnUiThread(modalityState, actions -> {
          if (!actions.isEmpty()) {
            hintAlarm.addRequest(() -> {
              if (ShowHideIntentionIconLookupAction.shouldShowLookupHint() &&
                  !((CompletionExtender)myList.getExpandableItemsHandler()).isShowing() &&
                  !processIcon.isVisible()) {
                hintButton.setVisible(true);
              }
            }, 500, modalityState);
          }
        })
        .submit(AppExecutorUtil.getAppExecutorService());
    }
  }

  void setCalculating(boolean calculating) {
    if (calculating) {
      processIcon.resume();
    }
    else {
      processIcon.suspend();
    }
    EdtScheduler.getInstance().schedule(100, modalityState, () -> {
      if (lookup.isLookupDisposed()) {
        return;
      }
      if (calculating && hintButton.isVisible()) {
        hintButton.setVisible(false);
      }
      processIcon.setVisible(calculating);

      ApplicationManager.getApplication().invokeLater(() -> {
        if (!calculating && !lookup.isLookupDisposed()) {
          updateHint();
        }
      }, modalityState);
    });
  }

  void refreshUi(boolean selectionVisible, boolean itemsChanged, boolean reused, boolean onExplicitAction) {
    Editor editor = lookup.getTopLevelEditor();
    if (editor.getComponent().getRootPane() == null || editor instanceof EditorWindow && !((EditorWindow)editor).isValid()) {
      return;
    }

    if (lookup.myResizePending || itemsChanged) {
      myMaximumHeight = Integer.MAX_VALUE;
    }
    Rectangle rectangle = calculatePosition();
    myMaximumHeight = rectangle.height;

    if (lookup.myResizePending || itemsChanged) {
      lookup.myResizePending = false;
      lookup.pack();
      rectangle = calculatePosition();
    }
    HintManagerImpl.updateLocation(lookup, editor, rectangle.getLocation());

    if (reused || selectionVisible || onExplicitAction) {
      lookup.ensureSelectionVisible(false);
    }
  }

  boolean isPositionedAboveCaret() {
    return myPositionedAbove != null && myPositionedAbove.booleanValue();
  }

  // in layered pane coordinate system.
  Rectangle calculatePosition() {
    final JComponent lookupComponent = lookup.getComponent();
    Dimension dim = lookupComponent.getPreferredSize();
    int lookupStart = lookup.getLookupStart();
    Editor editor = lookup.getTopLevelEditor();
    if (lookupStart < 0 || lookupStart > editor.getDocument().getTextLength()) {
      LOG.error(lookupStart + "; offset=" + editor.getCaretModel().getOffset() + "; element=" +
                lookup.getPsiElement());
    }

    LogicalPosition pos = editor.offsetToLogicalPosition(lookupStart);
    Point location = editor.logicalPositionToXY(pos);
    if (LOG.isDebugEnabled()) {
      LOG.debug("START calculating lookup bounds (above the line = " + myPositionedAbove
                + ") with preferred size " + dim
                + " for editor offset " + lookupStart
                + " positioned at " + location);
    }
    int lineHeight = editor.getLineHeight();
    location.y += lineHeight;
    int textIndent = lookup.cellRenderer.getTextIndent();
    location.x -= textIndent;
    if (LOG.isDebugEnabled()) {
      LOG.debug("Location after shifting by line height (" + lineHeight + ") and text indent (" + textIndent + "): " + location);
    }
    // extra check for other borders
    final Window window = ComponentUtil.getWindow(lookupComponent);
    if (window != null) {
      final Point point = SwingUtilities.convertPoint(lookupComponent, 0, 0, window);
      location.x -= point.x;
      if (LOG.isDebugEnabled()) {
        LOG.debug("Location after shifting by the window X coordinate (" + point.x + "): " + location);
      }
    }

    JComponent editorComponent = editor.getContentComponent();
    SwingUtilities.convertPointToScreen(location, editorComponent);
    final Rectangle screenRectangle = ScreenUtil.getScreenRectangle(editorComponent);
    if (LOG.isDebugEnabled()) {
      var editorLocation = editorComponent.getLocationOnScreen();
      LOG.debug("Location after converting to screen coordinates (editor component bounds " +
                new Rectangle(editorLocation, editorComponent.getSize()) +
                "): " +
                location);
      LOG.debug("Editor component screen rectangle is: " + screenRectangle);
    }

    int yLocationAboveCaret = location.y - lineHeight - dim.height;
    if (!isPositionedAboveCaret()) {
      int yScreenBottom = screenRectangle.y + screenRectangle.height;
      int yPopupBottom = location.y + dim.height;
      if (yPopupBottom > yScreenBottom && yLocationAboveCaret >= screenRectangle.y
          || lookup.getPresentation().getPositionStrategy() == LookupPositionStrategy.ONLY_ABOVE) {
        if (LOG.isDebugEnabled()) {
          String reason = lookup.getPresentation().getPositionStrategy() == LookupPositionStrategy.ONLY_ABOVE
                          ? "LookupPositionStrategy.ONLY_ABOVE is specified"
                          : "the popup won't fit below, but will fit above";
          LOG.debug("Positioning above the line because " + reason);
        }
        myPositionedAbove = true;
      }
      else {
        myPositionedAbove = false; // this assignment is necessary in case myPositionedAbove was to begin with
      }
    }
    if (isPositionedAboveCaret()) {
      location.y = yLocationAboveCaret;
      if (LOG.isDebugEnabled()) {
        LOG.debug("Location after shifting upwards by popup height plus line height to show above the line: " + location);
      }
    }

    if (!screenRectangle.contains(location)) {
      location = ScreenUtil.findNearestPointOnBorder(screenRectangle, location);
      if (LOG.isDebugEnabled()) {
        LOG.debug("Location after moving it to the nearest border because it doesn't fit into the screen: " + location);
      }
    }

    Rectangle candidate = new Rectangle(location, dim);
    ScreenUtil.cropRectangleToFitTheScreen(candidate);
    if (LOG.isDebugEnabled()) {
      LOG.debug("Bounds after cropping to fit into the screen: " + candidate);
    }

    if (isPositionedAboveCaret()) {
      // need to crop as well at bottom if lookup overlaps the current line
      Point caretLocation = editor.logicalPositionToXY(pos);
      SwingUtilities.convertPointToScreen(caretLocation, editorComponent);
      int offset = location.y + dim.height - caretLocation.y;
      if (offset > 0) {
        candidate.height -= offset;
        if (LOG.isDebugEnabled()) {
          LOG.debug("Bounds after cropping to avoid overlapping the current line: " + candidate);
        }
      }
    }

    JRootPane rootPane = editor.getComponent().getRootPane();
    if (rootPane != null) {
      SwingUtilities.convertPointFromScreen(location, rootPane.getLayeredPane());
      if (LOG.isDebugEnabled()) {
        var rootPaneLocation = rootPane.getLocationOnScreen();
        LOG.debug("Location after converting from screen coordinates (root pane bounds " +
                  new Rectangle(rootPaneLocation, rootPane.getSize()) +
                  "): " +
                  location);
      }
    }
    else {
      LOG.error("editor.disposed=" +
                editor.isDisposed() +
                "; lookup.disposed=" +
                lookup.isLookupDisposed() +
                "; editorShowing=" +
                editorComponent.isShowing());
    }

    myMaximumHeight = candidate.height;
    Rectangle result = new Rectangle(location.x, location.y, dim.width, candidate.height);
    if (LOG.isDebugEnabled()) {
      LOG.debug("END calculating lookup bounds, result: " + result);
    }
    return result;
  }

  private static final class ShowCompletionSettingsAction extends AnAction implements DumbAware {
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
          int maxCellWidth = lookup.cellRenderer.getLookupTextWidth() + lookup.cellRenderer.getTextIndent();
          int scrollBarWidth = myScrollPane.getVerticalScrollBar().getWidth();
          int listWidth = Math.min(scrollBarWidth + maxCellWidth, UISettings.getInstance().getMaxLookupWidth());

          Dimension bottomPanelSize = myBottomPanel != null ? myBottomPanel.getPreferredSize() : new Dimension();

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
            if (listHeight != myList.getModel().getSize() &&
                listHeight != myList.getVisibleRowCount() &&
                preferredSize.height != size.height) {
              lookup.getPresentation().setMaxVisibleItemsCount(listHeight);
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
      lookup.showElementActions(e.getInputEvent());
    }
  }

  private static final class MenuAction extends DefaultActionGroup implements HintManagerImpl.ActionToIgnore {
    MenuAction() {
      getTemplatePresentation().setIcon(AllIcons.Actions.More);
      getTemplatePresentation().putClientProperty(ActionButton.HIDE_DROPDOWN_ICON, Boolean.TRUE);
      getTemplatePresentation().setPopupGroup(true);
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
      lookup.resort(false);
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

  private final class LookupBottomLayout implements LayoutManager {
    @Override
    public void addLayoutComponent(String name, Component comp) { }

    @Override
    public void removeLayoutComponent(Component comp) { }

    @Override
    public Dimension preferredLayoutSize(Container parent) {
      Insets insets = parent.getInsets();
      Dimension adSize = myAdvertiser.getAdComponent().getPreferredSize();
      Dimension hintButtonSize = hintButton.getPreferredSize();
      Dimension menuButtonSize = myMenuButton.getPreferredSize();

      return new Dimension(adSize.width + hintButtonSize.width + menuButtonSize.width + insets.left + insets.right,
                           Math.max(adSize.height, menuButtonSize.height) + insets.top + insets.bottom);
    }

    @Override
    public Dimension minimumLayoutSize(Container parent) {
      Insets insets = parent.getInsets();
      Dimension adSize = myAdvertiser.getAdComponent().getMinimumSize();
      Dimension hintButtonSize = hintButton.getMinimumSize();
      Dimension menuButtonSize = myMenuButton.getMinimumSize();

      return new Dimension(adSize.width + hintButtonSize.width + menuButtonSize.width + insets.left + insets.right,
                           Math.max(adSize.height, menuButtonSize.height) + insets.top + insets.bottom);
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

      Dimension myHintButtonSize = hintButton.getPreferredSize();
      if (hintButton.isVisible() && !processIcon.isVisible()) {
        x -= myHintButtonSize.width;
        y = (innerHeight - myHintButtonSize.height) / 2;
        hintButton.setBounds(x, y + insets.top, myHintButtonSize.width, myHintButtonSize.height);
      }
      else if (!hintButton.isVisible() && processIcon.isVisible()) {
        Dimension myProcessIconSize = processIcon.getPreferredSize();
        x -= myProcessIconSize.width;
        y = (innerHeight - myProcessIconSize.height) / 2;
        processIcon.setBounds(x, y + insets.top, myProcessIconSize.width, myProcessIconSize.height);
      }
      else if (!hintButton.isVisible() && !processIcon.isVisible()) {
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
