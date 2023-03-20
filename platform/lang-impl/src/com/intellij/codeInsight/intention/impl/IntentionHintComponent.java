// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.hint.*;
import com.intellij.codeInsight.intention.CustomizableIntentionAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionActionDelegate;
import com.intellij.codeInsight.intention.impl.config.IntentionManagerSettings;
import com.intellij.codeInsight.intention.impl.preview.IntentionPreviewPopupUpdateProcessor;
import com.intellij.codeInsight.unwrap.ScopeHighlighter;
import com.intellij.codeInspection.SuppressIntentionActionFromFix;
import com.intellij.icons.AllIcons;
import com.intellij.ide.actions.ActionsCollector;
import com.intellij.ide.plugins.DynamicPlugins;
import com.intellij.ide.ui.UISettingsUtils;
import com.intellij.internal.statistic.IntentionsCollector;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.editor.actions.EditorActionUtil;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.refactoring.BaseRefactoringIntentionAction;
import com.intellij.ui.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.icons.RowIcon;
import com.intellij.ui.popup.WizardPopup;
import com.intellij.ui.popup.list.ListPopupImpl;
import com.intellij.util.Alarm;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ThreeState;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Shows a light bulb icon in the editor if some intention is available.
 * <ul>
 * <li>Hovering over the light bulb icon draws a border around the light bulb.
 * <li>Clicking the light bulb opens a popup menu that lists the available intentions.
 * </ul>
 *
 * @author max
 * @author Mike
 * @author Valentin
 * @author Eugene Belyaev
 * @author Konstantin Bulenkov
 */
public final class IntentionHintComponent implements Disposable, ScrollAwareHint {

  private static final Logger LOG = Logger.getInstance(IntentionHintComponent.class);

  private static final Alarm ourAlarm = new Alarm();

  private final Editor myEditor;
  private boolean myDisposed; // accessed in EDT only

  private final LightBulbPanel myLightBulbPanel;
  private final MyComponentHint myComponentHint;
  private final IntentionPopup myPopup;

  @RequiresEdt
  private IntentionHintComponent(@NotNull Project project,
                                 @NotNull PsiFile file,
                                 @NotNull Editor editor,
                                 @NotNull CachedIntentions cachedIntentions) {
    myEditor = editor;
    myPopup = new IntentionPopup(project, file, editor, cachedIntentions);
    Disposer.register(this, myPopup);

    myLightBulbPanel = new LightBulbPanel(project, file, editor, LightBulbUtil.getIcon(cachedIntentions));
    myComponentHint = new MyComponentHint(myLightBulbPanel);

    EditorUtil.disposeWithEditor(myEditor, this);
    DynamicPlugins.INSTANCE.onPluginUnload(this, () -> Disposer.dispose(this));
  }

  @RequiresEdt
  public static @NotNull IntentionHintComponent showIntentionHint(@NotNull Project project,
                                                                  @NotNull PsiFile file,
                                                                  @NotNull Editor editor,
                                                                  boolean showExpanded,
                                                                  @NotNull CachedIntentions cachedIntentions) {
    IntentionHintComponent component = new IntentionHintComponent(project, file, editor, cachedIntentions);

    if (editor.getSettings().isShowIntentionBulb()) {
      component.showIntentionHintImpl(!showExpanded);
    }
    if (showExpanded) {
      ApplicationManager.getApplication().invokeLater(() -> {
        if (!editor.isDisposed() && UIUtil.isShowing(editor.getContentComponent())) {
          component.showPopup(false);
        }
      }, project.getDisposed());
    }

    return component;
  }

  public boolean isVisible() {
    return myLightBulbPanel.isVisible();
  }

  public boolean isDisposed() {
    return myDisposed;
  }

  @Override
  @RequiresEdt
  public void dispose() {
    myDisposed = true;
    myComponentHint.hide();
    myLightBulbPanel.hide();
  }

  @Override
  @RequiresEdt
  public void editorScrolled() {
    myPopup.close();
  }

  public void hide() {
    myDisposed = true;
    Disposer.dispose(this);
  }

  /**
   * Hides this component if it is visible and bound to the supplied editor
   *
   * @param editor editor to check against
   * @return true if hidden successfully; false if it's not displayed already, or bound to another editor
   */
  public boolean hideIfDisplayedForEditor(@NotNull Editor editor) {
    if (isDisposed() || !isVisible() || editor != myEditor) return false;
    hide();
    return true;
  }

  public boolean hasVisibleLightBulbOrPopup() {
    return !isDisposed()
           && isVisible()
           && (myComponentHint.isVisible() || myPopup.isVisible() || ApplicationManager.getApplication().isUnitTestMode());
  }

  @TestOnly
  public CachedIntentions getCachedIntentions() {
    return myPopup.myCachedIntentions;
  }

  private void showIntentionHintImpl(boolean delay) {
    int offset = myEditor.getCaretModel().getOffset();

    myComponentHint.setShouldDelay(delay);

    HintManagerImpl hintManager = HintManagerImpl.getInstanceImpl();

    QuestionAction action = new PriorityQuestionAction() {
      @Override
      public boolean execute() {
        showPopup(false);
        return true;
      }

      @Override
      public int getPriority() {
        return -10;
      }
    };
    if (hintManager.canShowQuestionAction(action)) {
      Point position = LightBulbUtil.getPosition(myEditor);
      if (position != null) {
        hintManager.showQuestionHint(myEditor, position, offset, offset, myComponentHint, action, HintManager.ABOVE);
      }
    }
  }

  @TestOnly
  public LightweightHint getComponentHint() {
    return myComponentHint;
  }

  @RequiresEdt
  private void showPopup(boolean mouseClick) {
    RelativePoint positionHint = null;
    if (mouseClick && myLightBulbPanel.isShowing()) {
      RelativePoint swCorner = RelativePoint.getSouthWestOf(myLightBulbPanel);
      Point popup = swCorner.getPoint();

      Point panel = SwingUtilities.convertPoint(myLightBulbPanel, new Point(), myEditor.getContentComponent());
      Point caretLine = myEditor.offsetToXY(myEditor.getCaretModel().getOffset());
      if (panel.y + myLightBulbPanel.getHeight() <= caretLine.y) {
        // The light bulb panel is shown above the caret line.
        // The caret line should be completely visible, as it contains the interesting code.
        // The popup menu is shown below the caret line.
        popup.y += 1; // Step outside the light bulb panel.
        popup.y += myEditor.getLineHeight();
      }
      else {
        // Let the top border pixel of the popup menu overlap the bottom border pixel of the light bulb panel.
      }

      // XXX: This formula is only guessed.
      int adjust = (int)(UISettingsUtils.getInstance().getCurrentIdeScale() - 0.5);
      // Align the left border of the popup menu with the light bulb panel.
      // XXX: Where does the 1 come from?
      popup.x += 1 + adjust;
      // Align the top border of the menu bar.
      popup.y += adjust;

      positionHint = new RelativePoint(swCorner.getComponent(), popup);
    }
    myPopup.show(this, positionHint);
  }

  private static final class MyComponentHint extends LightweightHint {
    private boolean myVisible;
    private boolean myShouldDelay;

    private MyComponentHint(JComponent component) {
      super(component);
    }

    @Override
    public void show(@NotNull JComponent parentComponent, int x, int y, JComponent focusBackComponent, @NotNull HintHint hintHint) {
      myVisible = true;
      if (myShouldDelay) {
        ourAlarm.cancelAllRequests();
        ourAlarm.addRequest(() -> showImpl(parentComponent, x, y, focusBackComponent), 500);
      }
      else {
        showImpl(parentComponent, x, y, focusBackComponent);
      }
    }

    private void showImpl(JComponent parentComponent, int x, int y, JComponent focusBackComponent) {
      if (!parentComponent.isShowing()) return;
      super.show(parentComponent, x, y, focusBackComponent, new HintHint(parentComponent, new Point(x, y)));
    }

    @Override
    public void hide() {
      super.hide();
      myVisible = false;
      ourAlarm.cancelAllRequests();
    }

    @Override
    public boolean isVisible() {
      return myVisible || super.isVisible();
    }

    private void setShouldDelay(boolean shouldDelay) {
      myShouldDelay = shouldDelay;
    }
  }

  private abstract static class LightBulbUtil {

    private static final int NORMAL_BORDER_SIZE = 6;
    private static final int SMALL_BORDER_SIZE = 4;

    static @NotNull Icon getIcon(CachedIntentions cachedIntentions) {
      boolean showRefactoring = !ExperimentalUI.isNewUI() && ContainerUtil.exists(
        cachedIntentions.getInspectionFixes(),
        descriptor -> IntentionActionDelegate.unwrap(descriptor.getAction()) instanceof BaseRefactoringIntentionAction
      );
      if (showRefactoring) return AllIcons.Actions.RefactoringBulb;

      boolean showQuickFix = ContainerUtil.exists(
        cachedIntentions.getErrorFixes(),
        descriptor -> IntentionManagerSettings.getInstance().isShowLightBulb(descriptor.getAction())
      );
      if (showQuickFix) return AllIcons.Actions.QuickfixBulb;

      return AllIcons.Actions.IntentionBulb;
    }

    static Border createInactiveBorder(Editor editor) {
      return createEmptyBorder(getBorderSize(editor));
    }

    static Border createActiveBorder(Editor editor) {
      return BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(getBorderColor(), 1),
        createEmptyBorder(getBorderSize(editor) - 1)
      );
    }

    static int getBorderSize(Editor editor) {
      return editor.isOneLineMode() ? SMALL_BORDER_SIZE : NORMAL_BORDER_SIZE;
    }

    private static Border createEmptyBorder(int size) {
      return BorderFactory.createEmptyBorder(size, size, size, size);
    }

    private static Color getBorderColor() {
      return EditorColorsManager.getInstance().getGlobalScheme().getColor(EditorColors.SELECTED_TEARLINE_COLOR);
    }

    /** Returns the position of the light bulb, relative to {@link Editor#getComponent()}. */
    static @Nullable Point getPosition(Editor editor) {
      if (ApplicationManager.getApplication().isUnitTestMode()) return new Point();

      LOG.assertTrue(editor.getComponent().isDisplayable());

      return editor.isOneLineMode()
             ? getPositionOneLine(editor)
             : getPositionMultiLine(editor);
    }

    private static @NotNull Point getPositionOneLine(Editor editor) {
      JComponent convertComponent = editor.getContentComponent();

      // place the light bulb at the corner of the surrounding component
      JComboBox<?> ancestorCombo = Util.findAncestorCombo(editor);
      if (ancestorCombo != null) {
        convertComponent = ancestorCombo;
      }
      else {
        JTextField ancestorTextField = (JTextField)SwingUtilities.getAncestorOfClass(JTextField.class, editor.getContentComponent());
        if (ancestorTextField != null) {
          convertComponent = ancestorTextField;
        }
      }

      Point realPoint = new Point(-(EmptyIcon.ICON_16.getIconWidth() / 2) - 4, -(EmptyIcon.ICON_16.getIconHeight() / 2));
      Point p = SwingUtilities.convertPoint(convertComponent, realPoint, editor.getComponent().getRootPane().getLayeredPane());
      return new Point(p.x, p.y);
    }

    private static @Nullable Point getPositionMultiLine(Editor editor) {
      int visualCaretLine = editor.offsetToVisualPosition(editor.getCaretModel().getOffset()).line;
      int lineY = editor.visualPositionToXY(new VisualPosition(visualCaretLine, 0)).y;

      int iconWidth = EmptyIcon.ICON_16.getIconWidth();
      int iconHeight = EmptyIcon.ICON_16.getIconHeight();
      int panelWidth = NORMAL_BORDER_SIZE + iconWidth + iconWidth + NORMAL_BORDER_SIZE;
      int panelHeight = NORMAL_BORDER_SIZE + iconHeight + NORMAL_BORDER_SIZE;

      Rectangle visibleArea = editor.getScrollingModel().getVisibleArea();
      if (lineY < visibleArea.y) return null;
      int lineHeight = editor.getLineHeight();
      if (lineY + panelHeight >= visibleArea.y + visibleArea.height) return null;

      int x = visibleArea.x;
      int y;
      if (lineHeight >= iconHeight && fitsInCaretLine(editor, x + panelWidth)) {
        // Center the light bulb icon in the caret line.
        // The (usually invisible) border may be outside the caret line.
        y = lineY + (lineHeight - panelHeight) / 2;
      }
      else if (lineY - panelHeight >= visibleArea.y) {
        // Place the light bulb panel above the caret line.
        y = lineY - panelHeight;
      }
      else {
        // Place the light bulb panel below the caret line.
        y = lineY + lineHeight;
      }

      return SwingUtilities.convertPoint(editor.getContentComponent(), new Point(x, y),
                                         editor.getComponent().getRootPane().getLayeredPane());
    }

    private static boolean fitsInCaretLine(Editor editor, int windowRight) {
      if (ApplicationManager.getApplication().isUnitTestMode() || editor.isOneLineMode()) return false;
      if (Registry.is("always.show.intention.above.current.line", false)) return false;

      int visualCaretLine = editor.offsetToVisualPosition(editor.getCaretModel().getOffset()).line;
      int textColumn = EditorActionUtil.findFirstNonSpaceColumnOnTheLine(editor, visualCaretLine);
      if (textColumn == -1) return false;

      int safetyColumn = Math.max(0, textColumn - 2); // 2 characters safety margin, for IDEA-313840.
      int textX = editor.visualPositionToXY(new VisualPosition(visualCaretLine, safetyColumn)).x;
      return textX > windowRight;
    }
  }

  /** The light bulb icon, optionally surrounded by a border. */
  private class LightBulbPanel extends JPanel {
    private static final Icon ourInactiveArrowIcon = IconManager.getInstance().createEmptyIcon(AllIcons.General.ArrowDown);

    private final RowIcon myHighlightedIcon;
    private final RowIcon myInactiveIcon;
    private final JLabel myIconLabel;

    LightBulbPanel(@NotNull Project project, @NotNull PsiFile file, @NotNull Editor editor, @NotNull Icon smartTagIcon) {
      setLayout(new BorderLayout());
      setOpaque(false);

      IconManager iconManager = IconManager.getInstance();
      myHighlightedIcon = iconManager.createRowIcon(smartTagIcon, AllIcons.General.ArrowDown);
      myInactiveIcon = iconManager.createRowIcon(smartTagIcon, ourInactiveArrowIcon);

      myIconLabel = new JLabel(myInactiveIcon);
      myIconLabel.setOpaque(false);
      myIconLabel.addMouseListener(new LightBulbMouseListener(project, file));

      add(myIconLabel, BorderLayout.CENTER);
      setBorder(LightBulbUtil.createInactiveBorder(editor));
    }

    @Override
    public synchronized void addMouseListener(MouseListener l) {
      // Avoid this (transparent) panel consuming mouse click events, see IDEA-171695.
      // This is a dirty hack since it shows an inconsistent mouse cursor.
    }

    @RequiresEdt
    private void onMouseExit() {
      if (!myPopup.isVisible()) {
        myIconLabel.setIcon(myInactiveIcon);
        setBorder(LightBulbUtil.createInactiveBorder(myEditor));
      }
    }

    private void onMouseEnter() {
      myIconLabel.setIcon(myHighlightedIcon);
      setBorder(LightBulbUtil.createActiveBorder(myEditor));

      String acceleratorsText = KeymapUtil.getFirstKeyboardShortcutText(
        ActionManager.getInstance().getAction(IdeActions.ACTION_SHOW_INTENTION_ACTIONS));
      if (!acceleratorsText.isEmpty()) {
        myIconLabel.setToolTipText(CodeInsightBundle.message("lightbulb.tooltip", acceleratorsText));
      }
    }
  }

  // IDEA-313550: Intention light bulb border is calculated wrong
  private class LightBulbMouseListener extends MouseAdapter {
    private final @NotNull Project myProject;
    private final @NotNull PsiFile myFile;

    LightBulbMouseListener(@NotNull Project project, @NotNull PsiFile file) {
      this.myProject = project;
      this.myFile = file;
    }

    @Override
    public void mousePressed(@NotNull MouseEvent e) {
      if (!e.isPopupTrigger() && e.getButton() == MouseEvent.BUTTON1) {
        logMousePressed(e);
        showPopup(true);
      }
    }

    @Override
    public void mouseEntered(@NotNull MouseEvent e) {
      myLightBulbPanel.onMouseEnter();
    }

    @Override
    public void mouseExited(@NotNull MouseEvent e) {
      myLightBulbPanel.onMouseExit();
    }

    private void logMousePressed(@NotNull MouseEvent e) {
      AnAction action = ActionManager.getInstance().getAction(IdeActions.ACTION_SHOW_INTENTION_ACTIONS);
      DataContext projectContext = SimpleDataContext.getProjectContext(myProject);
      AnActionEvent event = AnActionEvent.createFromInputEvent(e, ActionPlaces.MOUSE_SHORTCUT, null, projectContext);
      ActionsCollector.getInstance().record(myProject, action, event, myFile.getLanguage());
    }
  }

  static class IntentionPopup implements Disposable.Parent {
    private final @NotNull Project myProject;
    private final @NotNull Editor myEditor;
    private final @NotNull PsiFile myFile;
    private final @NotNull CachedIntentions myCachedIntentions;
    private final IntentionPreviewPopupUpdateProcessor myPreviewPopupUpdateProcessor;
    private PopupMenuListener myOuterComboboxPopupListener;
    private IntentionHintComponent myHint;
    private ListPopup myListPopup;
    private boolean myDisposed;
    private boolean myPopupShown;

    private IntentionPopup(@NotNull Project project,
                           @NotNull PsiFile file,
                           @NotNull Editor editor,
                           @NotNull CachedIntentions cachedIntentions) {
      myProject = project;
      myEditor = editor;
      myFile = file;
      myCachedIntentions = cachedIntentions;
      myPreviewPopupUpdateProcessor = new IntentionPreviewPopupUpdateProcessor(project, myFile, myEditor);
    }

    private boolean isVisible() {
      return myListPopup != null && SwingUtilities.getWindowAncestor(myListPopup.getContent()) != null;
    }

    private void show(@NotNull IntentionHintComponent component, @Nullable RelativePoint positionHint) {
      if (myDisposed || myEditor.isDisposed() || (myListPopup != null && myListPopup.isDisposed()) || myPopupShown) return;

      if (myListPopup == null) {
        assert myHint == null;
        myHint = component;
        recreateMyPopup(this, new IntentionListStep(this, myEditor, myFile, myProject, myCachedIntentions));
      }
      else {
        assert myHint == component;
      }

      if (positionHint != null) {
        myListPopup.show(positionHint);
      }
      else {
        myListPopup.showInBestPositionFor(myEditor);
      }

      if (EditorSettingsExternalizable.getInstance().isShowIntentionPreview()) {
        ApplicationManager.getApplication().invokeLater(this::showPreview);
      }

      IntentionsCollector.reportShownIntentions(myFile.getProject(), myListPopup, myFile.getLanguage());
      myPopupShown = true;
    }

    private void close() {
      myListPopup.cancel();
      myPopupShown = false;
    }

    @RequiresEdt
    void cancelled(@NotNull IntentionListStep step) {
      if (myListPopup.getListStep() == step && !myDisposed) {
        Disposer.dispose(myHint);
      }
    }

    /**
     * Hide preview temporarily when submenu is shown
     */
    void hidePreview() {
      myPreviewPopupUpdateProcessor.hideTemporarily();
    }

    @Override
    public void beforeTreeDispose() {
      // The flag has to be set early. Child's dispose() can call `cancelled` and it must be a no-op at this point.
      myDisposed = true;
    }

    @Override
    public void dispose() {
      if (myOuterComboboxPopupListener != null) {
        JComboBox<?> ancestor = Util.findAncestorCombo(myEditor);
        if (ancestor != null) {
          ancestor.removePopupMenuListener(myOuterComboboxPopupListener);
        }

        myOuterComboboxPopupListener = null;
      }
    }

    @RequiresEdt
    private static void recreateMyPopup(@NotNull IntentionPopup popup, @NotNull ListPopupStep<IntentionActionWithTextCaching> step) {
      if (popup.myListPopup != null) {
        Disposer.dispose(popup.myListPopup);
      }
      if (popup.myDisposed || popup.myEditor.isDisposed()) {
        popup.myListPopup = null;
        return;
      }
      popup.myListPopup = JBPopupFactory.getInstance().createListPopup(step);
      if (popup.myListPopup instanceof WizardPopup wizardPopup) {
        Shortcut[] shortcuts = KeymapUtil.getActiveKeymapShortcuts(IdeActions.ACTION_SHOW_INTENTION_ACTIONS).getShortcuts();
        for (Shortcut shortcut : shortcuts) {
          if (shortcut instanceof KeyboardShortcut keyboardShortcut && keyboardShortcut.getSecondKeyStroke() == null) {
            wizardPopup.registerAction(
              "activateSelectedElement", keyboardShortcut.getFirstKeyStroke(),
              Util.createAction(e -> popup.myListPopup.handleSelect(true))
            );
          }
        }

        popup.registerIntentionShortcuts();
        popup.registerShowPreviewAction();
      }

      HighlightingContext context = new HighlightingContext(popup);

      ListPopupImpl list = ObjectUtils.tryCast(popup.myListPopup, ListPopupImpl.class);

      var selectionListener = new ListSelectionListener() {
        @Override
        public void valueChanged(ListSelectionEvent e) {
          Object source = e.getSource();

          if (source instanceof DataProvider dataProvider) {
            Object selectedItem = PlatformCoreDataKeys.SELECTED_ITEM.getData(dataProvider);
            if (selectedItem instanceof IntentionActionWithTextCaching actionWithCaching) {
              IntentionAction action = IntentionActionDelegate.unwrap(actionWithCaching.getAction());
              if (list != null) {
                popup.updatePreviewPopup(action, list.getOriginalSelectedIndex());
              }
              highlightOnHover(selectedItem);
              return;
            }
          }
          context.dropHighlight();
        }

        private void highlightOnHover(Object selectedItem) {
          if (!(selectedItem instanceof IntentionActionWithTextCaching actionWithCaching)) return;
          IntentionAction action = IntentionActionDelegate.unwrap(actionWithCaching.getAction());

          if (context.mayHaveHighlighting(action)) {
            ReadAction.nonBlocking(() -> context.computeHighlightsToApply(action))
              .coalesceBy(popup)
              .finishOnUiThread(ModalityState.any(), Runnable::run)
              .submit(AppExecutorUtil.getAppExecutorService());
          } else {
            context.dropHighlight();
          }
        }
      };
      popup.myListPopup.addListSelectionListener(selectionListener);

      popup.myListPopup.addListener(new JBPopupListener() {
        @Override
        public void beforeShown(@NotNull LightweightWindowEvent event) {
          if (list != null) {
            selectionListener.highlightOnHover(list.getList().getSelectedValue());
          }
        }

        @Override
        public void onClosed(@NotNull LightweightWindowEvent event) {
          context.dropHighlight();
          popup.myPreviewPopupUpdateProcessor.hide();
          popup.myPopupShown = false;
        }
      });

      if (popup.myEditor.isOneLineMode()) {
        // hide popup on combobox popup show
        JComboBox<?> comboBox = Util.findAncestorCombo(popup.myEditor);
        if (comboBox != null) {
          popup.myOuterComboboxPopupListener = new PopupMenuListenerAdapter() {
            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
              popup.myHint.hide();
            }
          };

          comboBox.addPopupMenuListener(popup.myOuterComboboxPopupListener);
        }
      }

      Disposer.register(popup, popup.myListPopup);
      Disposer.register(popup.myListPopup, ApplicationManager.getApplication()::assertIsDispatchThread);
    }

    /**
     * Manages highlighting in the editor when action defines it.
     * @see SuppressIntentionActionFromFix#getContainer(PsiElement) 
     * @see CustomizableIntentionAction#getRangesToHighlight(Editor, PsiFile) 
     */
    private static final class HighlightingContext {
      private final IntentionPopup myPopup;
      private PsiFile injectedFile;
      private Editor injectedEditor;
      private ScopeHighlighter highlighter;
      private volatile ScopeHighlighter injectionHighlighter;

      HighlightingContext(IntentionPopup popup) {
        myPopup = popup;
      }

      private void init() {
        if (injectionHighlighter != null) return;
        synchronized (this) {
          if (injectionHighlighter != null) return;
          PsiFile file = myPopup.myFile;
          Editor editor = myPopup.myEditor;
          boolean committed = PsiDocumentManager.getInstance(file.getProject()).isCommitted(editor.getDocument());
          injectedFile = committed
                         ? InjectedLanguageUtil.findInjectedPsiNoCommit(file, editor.getCaretModel().getOffset())
                         : null;
          injectedEditor = InjectedLanguageUtil.getInjectedEditorForInjectedFile(editor, injectedFile);

          highlighter = new ScopeHighlighter(editor);
          injectionHighlighter = new ScopeHighlighter(injectedEditor);
        }
      }

      private void dropHighlight() {
        if (injectionHighlighter != null) {
          highlighter.dropHighlight();
          injectionHighlighter.dropHighlight();
        }
      }
      
      boolean mayHaveHighlighting(@NotNull IntentionAction action) {
        return action instanceof SuppressIntentionActionFromFix || action instanceof CustomizableIntentionAction;
      }

      @NotNull
      Runnable computeHighlightsToApply(@NotNull IntentionAction action) {
        if (action instanceof SuppressIntentionActionFromFix suppressAction) {
          init();
          if (injectedFile != null && suppressAction.isShouldBeAppliedToInjectionHost() == ThreeState.NO) {
            PsiElement at = injectedFile.findElementAt(injectedEditor.getCaretModel().getOffset());
            PsiElement container = suppressAction.getContainer(at);
            if (container != null) {
              return () -> injectionHighlighter.highlight(container, Collections.singletonList(container));
            }
          }
          else {
            PsiElement at = myPopup.myFile.findElementAt(myPopup.myEditor.getCaretModel().getOffset());
            PsiElement container = suppressAction.getContainer(at);
            if (container != null) {
              return () -> highlighter.highlight(container, Collections.singletonList(container));
            }
          }
        }
        else if (action instanceof CustomizableIntentionAction customizableAction) {
          init();
          var ranges = customizableAction.getRangesToHighlight(myPopup.myEditor, myPopup.myFile);
          List<Runnable> actions = new ArrayList<>();
          for (var range : ranges) {
            TextRange rangeInFile = range.getRangeInFile();
            PsiFile file = range.getContainingFile();
            if (injectedFile != null && file.getViewProvider() == injectedFile.getViewProvider()) {
              actions.add(() -> injectionHighlighter.addHighlights(List.of(rangeInFile), range.getHighlightKey()));
            }
            else if (!InjectedLanguageManager.getInstance(myPopup.myProject).isInjectedFragment(file)) {
              actions.add(() -> highlighter.addHighlights(List.of(rangeInFile), range.getHighlightKey()));
            }
          }
          return () -> actions.forEach(Runnable::run);
        }
        return () -> {
        };
      }
    }

    @RequiresEdt
    private void updatePreviewPopup(@NotNull IntentionAction action, int index) {
      myPreviewPopupUpdateProcessor.setup(myListPopup, index);
      myPreviewPopupUpdateProcessor.updatePopup(action);
    }

    /** Add all intention shortcuts to also be available as actions in the popover */
    private void registerIntentionShortcuts() {
      for (Object object : myListPopup.getListStep().getValues()) {
        if (object instanceof IntentionActionDelegate delegate) {
          registerIntentionShortcut(delegate.getDelegate());
        }
      }
    }

    private void registerIntentionShortcut(@NotNull IntentionAction intention) {
      var shortcuts = IntentionShortcutManager.getInstance().getShortcutSet(intention);
      if (shortcuts == null) return;

      for (var shortcut : shortcuts.getShortcuts()) {
        if (shortcut instanceof KeyboardShortcut keyboardShortcut) {
          ((WizardPopup)myListPopup).registerAction(
            IntentionShortcutUtils.getWrappedActionId(intention), keyboardShortcut.getFirstKeyStroke(), Util.createAction(e -> {
              close();
              IntentionShortcutUtils.invokeAsAction(intention, myEditor, myFile);
            })
          );
        }
      }
    }

    @RequiresEdt
    private void registerShowPreviewAction() {
      KeyStroke keyStroke = KeymapUtil.getKeyStroke(IntentionPreviewPopupUpdateProcessor.Companion.getShortcutSet());
      Action action = Util.createAction(e -> maybeShowPreview());
      ((WizardPopup)myListPopup).registerAction("showIntentionPreview", keyStroke, action);
      advertisePopup(myListPopup);
    }

    private void maybeShowPreview() {
      IntentionPreviewPopupUpdateProcessor processor = myPreviewPopupUpdateProcessor;
      boolean shouldShow = !processor.isShown();
      EditorSettingsExternalizable.getInstance().setShowIntentionPreview(shouldShow);
      if (shouldShow) {
        processor.activate();
        showPreview();
      }
      else {
        processor.hide();
      }
    }

    private void showPreview() {
      myPreviewPopupUpdateProcessor.show();
      if (myListPopup instanceof ListPopupImpl listPopup) {
        JList<?> list = listPopup.getList();
        if (list.getSelectedValue() instanceof IntentionActionWithTextCaching actionWithCaching) {
          updatePreviewPopup(actionWithCaching.getAction(), list.getSelectedIndex());
        }
      }
    }

    private static void advertisePopup(@NotNull ListPopup popup) {
      if (!popup.isDisposed()) {
        String shortcutText = IntentionPreviewPopupUpdateProcessor.Companion.getShortcutText();
        popup.setAdText(CodeInsightBundle.message("intention.preview.adv.toggle.text", shortcutText), SwingConstants.LEFT);
      }
    }
  }

  private static final class Util {
    static Action createAction(Consumer<ActionEvent> perform) {
      return new AbstractAction() {
        @Override
        public void actionPerformed(ActionEvent e) {
          perform.accept(e);
        }
      };
    }

    static JComboBox<?> findAncestorCombo(Editor editor) {
      return (JComboBox<?>)SwingUtilities.getAncestorOfClass(JComboBox.class, editor.getContentComponent());
    }
  }
}
