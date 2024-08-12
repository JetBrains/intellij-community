// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.documentation.DocumentationComponent;
import com.intellij.codeInsight.documentation.DocumentationManager;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.ide.IdeEventQueue;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ModalityStateListener;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.editor.impl.EditorMouseHoverPopupControl;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.util.PopupUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.reference.SoftReference;
import com.intellij.ui.MouseMovementTracker;
import com.intellij.ui.codeFloatingToolbar.CodeFloatingToolbar;
import com.intellij.ui.popup.AbstractPopup;
import com.intellij.ui.popup.PopupFactoryImpl;
import com.intellij.util.Alarm;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.CancellablePromise;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.Objects;

import static com.intellij.lang.documentation.ide.impl.DocumentationTargetHoverInfoKt.calcTargetDocumentationInfo;

public class EditorMouseHoverPopupManager implements Disposable {
  static final Logger LOG = Logger.getInstance(EditorMouseHoverPopupManager.class);
  public static final int MAX_QUICK_DOC_CHARACTERS = 100_000;

  protected final Alarm myAlarm;
  private final MouseMovementTracker myMouseMovementTracker = new MouseMovementTracker();
  private boolean myKeepPopupOnMouseMove;
  private Reference<Editor> myCurrentEditor;
  private Reference<AbstractPopup> myPopupReference;
  protected Context myContext;
  private ProgressIndicator myCurrentProgress;
  private CancellablePromise<Context> myPreparationTask;
  private boolean mySkipNextMovement;

  public EditorMouseHoverPopupManager() {
    myAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, this);
    EditorEventMulticaster multicaster = EditorFactory.getInstance().getEventMulticaster();
    multicaster.addCaretListener(new CaretListener() {
      @Override
      public void caretPositionChanged(@NotNull CaretEvent event) {
        Editor editor = event.getEditor();
        if (editor == SoftReference.dereference(myCurrentEditor)) {
          //noinspection deprecation
          DocumentationManager.getInstance(Objects.requireNonNull(editor.getProject())).setAllowContentUpdateFromContext(true);
        }
      }
    }, this);
    multicaster.addVisibleAreaListener(e -> {
      Rectangle oldRectangle = e.getOldRectangle();
      if (e.getEditor() == SoftReference.dereference(myCurrentEditor) &&
          oldRectangle != null && !oldRectangle.getLocation().equals(e.getNewRectangle().getLocation())) {
        cancelProcessingAndCloseHint();
      }
    }, this);

    EditorMouseHoverPopupControl.getInstance().addListener(() -> {
      Editor editor = SoftReference.dereference(myCurrentEditor);
      if (editor != null && EditorMouseHoverPopupControl.arePopupsDisabled(editor)) {
        closeHint();
      }
    });
    LaterInvocator.addModalityStateListener(new ModalityStateListener() {
      @Override
      public void beforeModalityStateChanged(boolean entering, @NotNull Object modalEntity) {
        cancelProcessingAndCloseHint();
      }
    }, this);
    IdeEventQueue.getInstance().addDispatcher(event -> {
      if (event.getID() == KeyEvent.KEY_PRESSED) {
        cancelCurrentProcessing();
      }
      return false;
    }, this);
    ApplicationManager.getApplication().getMessageBus().connect(this).subscribe(AnActionListener.TOPIC, new MyActionListener());
  }

  @Override
  public void dispose() {}

  protected void handleMouseMoved(@NotNull EditorMouseEvent e) {
    cancelCurrentProcessing();

    if (ignoreEvent(e)) return;

    Editor editor = e.getEditor();
    if (isPopupDisabled(editor)) {
      closeHint();
      return;
    }

    showInfoTooltip(e, false);
  }

  private void cancelCurrentProcessing() {
    if (myPreparationTask != null) {
      myPreparationTask.cancel();
      myPreparationTask = null;
    }
    myAlarm.cancelAllRequests();
    if (myCurrentProgress != null) {
      myCurrentProgress.cancel();
      myCurrentProgress = null;
    }
  }

  private void skipNextMovement() {
    mySkipNextMovement = true;
  }

  private void scheduleProcessing(@NotNull Editor editor,
                                  @NotNull Context context,
                                  boolean updateExistingPopup,
                                  boolean forceShowing,
                                  boolean requestFocus) {
    ProgressIndicatorBase progress = new ProgressIndicatorBase();
    progress.setModalityProgress(null);
    if (!forceShowing) { // myCurrentProgress is cancelled on every mouse moved - do not use it for forceShowing mode
      myCurrentProgress = progress;
    }
    myAlarm.addRequest(() -> {
      ProgressManager.getInstance().executeProcessUnderProgress(() -> {
        // errors are stored in the top level editor markup model, not the injected one
        Editor topLevelEditor = InjectedLanguageUtil.getTopLevelEditor(editor);

        EditorHoverInfo info = context.calcInfo(topLevelEditor);
        ApplicationManager.getApplication().invokeLater(() -> {
          if (!forceShowing && progress != myCurrentProgress) {
            return;
          }

          myCurrentProgress = null;
          if (info == null ||
              !UIUtil.isShowing(topLevelEditor.getContentComponent()) ||
              (!forceShowing && isPopupDisabled(topLevelEditor))) {
            return;
          }

          VisualPosition position = context.getPopupPosition(topLevelEditor);
          PopupBridge popupBridge = new PopupBridge(editor, position);
          JComponent component = info.createComponent(topLevelEditor, popupBridge, requestFocus);
          if (component == null) {
            closeHint();
          }
          else {
            if (updateExistingPopup && isHintShown()) {
              updateHint(component, popupBridge);
            }
            else {
              AbstractPopup hint = createHint(component, popupBridge, requestFocus);
              showHintInEditor(hint, topLevelEditor, position);
              CodeFloatingToolbar floatingToolbar = CodeFloatingToolbar.getToolbar(editor);
              if (floatingToolbar != null) floatingToolbar.hideOnPopupConflict(hint);
              myPopupReference = new WeakReference<>(hint);
              myCurrentEditor = new WeakReference<>(topLevelEditor);
            }
            myContext = context;
          }
        });
      }, progress);
    }, context.getShowingDelay());
  }

  private boolean ignoreEvent(EditorMouseEvent e) {
    if (mySkipNextMovement) {
      mySkipNextMovement = false;
      return true;
    }
    Rectangle currentHintBounds = getCurrentHintBounds(e.getEditor());
    return myMouseMovementTracker.isMovingTowards(e.getMouseEvent(), currentHintBounds) ||
           currentHintBounds != null && myKeepPopupOnMouseMove;
  }

  private static boolean isPopupDisabled(Editor editor) {
    return isAnotherAppInFocus() ||
           EditorMouseHoverPopupControl.arePopupsDisabled(editor) ||
           LookupManager.getActiveLookup(editor) != null ||
           isAnotherPopupFocused() ||
           isContextMenuShown();
  }

  private static boolean isAnotherAppInFocus() {
    return KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow() == null &&
           !ApplicationManager.getApplication().isUnitTestMode();
  }

  // e.g., if documentation popup (opened via keyboard shortcut) is already shown
  private static boolean isAnotherPopupFocused() {
    JBPopup popup = PopupUtil.getPopupContainerFor(KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner());
    return popup != null && !popup.isDisposed();
  }

  private static boolean isContextMenuShown() {
    return MenuSelectionManager.defaultManager().getSelectedPath().length > 0;
  }

  private Rectangle getCurrentHintBounds(Editor editor) {
    JBPopup popup = getCurrentHint();
    if (popup == null) return null;
    Dimension size = popup.getSize();
    if (size == null) return null;
    Rectangle result = new Rectangle(popup.getLocationOnScreen(), size);
    int borderTolerance = editor.getLineHeight() / 3;
    result.grow(borderTolerance, borderTolerance);
    return result;
  }

  private void showHintInEditor(AbstractPopup hint, Editor editor, @NotNull VisualPosition position) {
    closeHint();
    myMouseMovementTracker.reset();
    myKeepPopupOnMouseMove = false;
    editor.putUserData(PopupFactoryImpl.ANCHOR_POPUP_POSITION, position);
    try {
      hint.showInBestPositionFor(editor);
    }
    finally {
      editor.putUserData(PopupFactoryImpl.ANCHOR_POPUP_POSITION, null);
    }
    Window window = hint.getPopupWindow();
    if (window != null) {
      window.setFocusableWindowState(true);
      IdeEventQueue.getInstance().addDispatcher(e -> {
        if (e.getID() == MouseEvent.MOUSE_PRESSED && e.getSource() == window) {
          myKeepPopupOnMouseMove = true;
        }
        else if (e.getID() == WindowEvent.WINDOW_OPENED && !isParentWindow(window, e.getSource())) {
          closeHint();
        }
        return false;
      }, hint);
    }
  }

  private static boolean isParentWindow(@NotNull Window parent, Object potentialChild) {
    return parent == potentialChild ||
           (potentialChild instanceof Component) && isParentWindow(parent, ((Component)potentialChild).getParent());
  }

  protected static AbstractPopup createHint(JComponent component, PopupBridge popupBridge, boolean requestFocus) {
    WrapperPanel wrapper = new WrapperPanel(component);
    AbstractPopup popup = (AbstractPopup)JBPopupFactory.getInstance()
      .createComponentPopupBuilder(wrapper, component)
      .setResizable(true)
      .setFocusable(requestFocus)
      .setRequestFocus(requestFocus)
      .setModalContext(false)
      .createPopup();
    popupBridge.setPopup(popup);
    return popup;
  }

  private void updateHint(JComponent component, PopupBridge popupBridge) {
    AbstractPopup popup = getCurrentHint();
    if (popup != null) {
      WrapperPanel wrapper = (WrapperPanel)popup.getComponent();
      wrapper.setContent(component);
      validatePopupSize(popup);
      popupBridge.setPopup(popup);
    }
  }

  static void validatePopupSize(@NotNull AbstractPopup popup) {
    JComponent component = popup.getComponent();
    if (component != null) popup.setSize(component.getPreferredSize());
  }

  private static int getTargetOffset(EditorMouseEvent event) {
    Editor editor = event.getEditor();
    if (editor instanceof EditorEx &&
        editor.getProject() != null &&
        event.getArea() == EditorMouseEventArea.EDITING_AREA &&
        event.getMouseEvent().getModifiers() == 0 &&
        event.isOverText() &&
        event.getCollapsedFoldRegion() == null) {
      return event.getOffset();
    }
    return -1;
  }

  private static @Nullable Context createContext(@NotNull Editor editor,
                                                 int offset,
                                                 long startTimestamp,
                                                 boolean showImmediately,
                                                 boolean showDocumentation) {
    Project project = Objects.requireNonNull(editor.getProject());

    HighlightInfo info = null;
    if (!Registry.is("ide.disable.editor.tooltips")) {
      DaemonCodeAnalyzerImpl daemonCodeAnalyzer = (DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(project);
      boolean highestPriorityOnly = !Registry.is("ide.tooltip.showAllSeverities");
      info = daemonCodeAnalyzer
        .findHighlightsByOffset(editor.getDocument(), offset, false, highestPriorityOnly, HighlightInfoType.SYMBOL_TYPE_SEVERITY);
    }

    PsiElement elementForQuickDoc = findElementForQuickDoc(editor, offset, project);
    return info == null && elementForQuickDoc == null
           ? null
           : new Context(startTimestamp, offset, info, elementForQuickDoc, showImmediately, showDocumentation);
  }

  private static @Nullable PsiElement findElementForQuickDoc(@NotNull Editor editor, int offset, @NotNull Project project) {
    if (!EditorSettingsExternalizable.getInstance().isShowQuickDocOnMouseOverElement()) {
      return null;
    }
    PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    if (psiFile == null) {
      return null;
    }
    PsiElement result = findElementForQuickDoc(project, psiFile, offset);
    if (result instanceof PsiWhiteSpace) {
      return null;
    }
    return result;
  }

  private static @Nullable PsiElement findElementForQuickDoc(@NotNull Project project, @NotNull PsiFile psiFile, int offset) {
    PsiElement injected = InjectedLanguageManager.getInstance(project).findInjectedElementAt(psiFile, offset);
    return injected != null
           ? injected
           : psiFile.findElementAt(offset);
  }

  protected void cancelProcessingAndCloseHint() {
    cancelCurrentProcessing();
    closeHint();
  }

  private void closeHint() {
    AbstractPopup hint = getCurrentHint();
    if (hint != null) {
      hint.cancel();
    }
    myPopupReference = null;
    myCurrentEditor = null;
    myContext = null;
  }

  @ApiStatus.Internal
  public boolean isHintShown() {
    return getCurrentHint() != null;
  }

  private AbstractPopup getCurrentHint() {
    if (myPopupReference == null) return null;
    AbstractPopup hint = myPopupReference.get();
    if (hint == null || !hint.isVisible()) {
      if (hint != null) {
        // hint's window might've been hidden by AWT without notifying us
        // dispose to remove the popup from IDE hierarchy and avoid leaking components
        hint.cancel();
      }
      myPopupReference = null;
      myCurrentEditor = null;
      myContext = null;
      return null;
    }
    return hint;
  }

  private void showInfoTooltip(@NotNull EditorMouseEvent e, boolean showImmediately) {
    long startTimestamp = System.currentTimeMillis();
    Editor editor = e.getEditor();
    int targetOffset = getTargetOffset(e);
    if (targetOffset < 0) {
      closeHint();
      return;
    }
    myPreparationTask = ReadAction.nonBlocking(() -> createContext(editor, targetOffset, startTimestamp, showImmediately, true))
      .coalesceBy(this)
      .withDocumentsCommitted(Objects.requireNonNull(editor.getProject()))
      .expireWhen(() -> editor.isDisposed())
      .finishOnUiThread(ModalityState.any(), context -> {
        myPreparationTask = null;
        if (context == null || !UIUtil.isShowing(editor.getContentComponent())) {
          closeHint();
          return;
        }
        Context.Relation relation = isHintShown() ? context.compareTo(myContext) : Context.Relation.DIFFERENT;
        if (relation == Context.Relation.SAME) {
          return;
        }
        else if (relation == Context.Relation.DIFFERENT) {
          closeHint();
        }
        scheduleProcessing(editor, context, relation == Context.Relation.SIMILAR, showImmediately, false);
      })
      .submit(AppExecutorUtil.getAppExecutorService());
  }

  public void showInfoTooltip(EditorMouseEvent e) {
    showInfoTooltip(e, true);
  }

  public void showInfoTooltip(@NotNull Editor editor,
                              @NotNull HighlightInfo info,
                              int offset,
                              boolean requestFocus,
                              boolean showImmediately) {
    showInfoTooltip(editor, info, offset, requestFocus, showImmediately, false);
  }

  public void showInfoTooltip(@NotNull Editor editor,
                              @NotNull HighlightInfo info,
                              int offset,
                              boolean requestFocus,
                              boolean showImmediately,
                              boolean showDocumentation) {
    if (editor.getProject() == null) return;
    cancelProcessingAndCloseHint();
    Context context = new Context(System.currentTimeMillis(), offset, info, null, showImmediately, showDocumentation);
    scheduleProcessing(editor, context, false, true, requestFocus);
  }

  private static class Context {
    private final long startTimestamp;
    private final boolean showImmediately;
    private final boolean showDocumentation;
    private final int targetOffset;
    private final WeakReference<HighlightInfo> highlightInfo;
    private final WeakReference<PsiElement> elementForQuickDoc;

    protected Context(long startTimestamp,
                      int targetOffset,
                      HighlightInfo highlightInfo,
                      PsiElement elementForQuickDoc,
                      boolean showImmediately,
                      boolean showDocumentation) {
      this.startTimestamp = startTimestamp;
      this.targetOffset = targetOffset;
      this.highlightInfo = highlightInfo == null ? null : new WeakReference<>(highlightInfo);
      this.elementForQuickDoc = elementForQuickDoc == null ? null : new WeakReference<>(elementForQuickDoc);
      this.showImmediately = showImmediately;
      this.showDocumentation = showDocumentation;
    }

    @Nullable PsiElement getElementForQuickDoc() {
      return SoftReference.dereference(elementForQuickDoc);
    }

    public boolean showDocumentation() {
      return showDocumentation;
    }

    public HighlightInfo getHighlightInfo() {
      return SoftReference.dereference(highlightInfo);
    }

    public int getTargetOffset() {
      return targetOffset;
    }

    private Relation compareTo(Context other) {
      if (other == null) return Relation.DIFFERENT;
      HighlightInfo highlightInfo = getHighlightInfo();
      if (!Objects.equals(highlightInfo, other.getHighlightInfo())) return Relation.DIFFERENT;
      return Objects.equals(getElementForQuickDoc(), other.getElementForQuickDoc())
             ? Relation.SAME
             : highlightInfo == null ? Relation.DIFFERENT : Relation.SIMILAR;
    }

    long getShowingDelay() {
      if (showImmediately) {
        return 0;
      }
      return Math.max(0, EditorSettingsExternalizable.getInstance().getTooltipsDelay() - (System.currentTimeMillis() - startTimestamp));
    }

    @NotNull VisualPosition getPopupPosition(Editor editor) {
      HighlightInfo highlightInfo = getHighlightInfo();
      if (highlightInfo == null) {
        return HoverDocPopupLocationProvider.Companion.getInstance().getPopupPosition(targetOffset, getElementForQuickDoc(), editor);
      }
      VisualPosition targetPosition = editor.offsetToVisualPosition(targetOffset);
      VisualPosition endPosition = editor.offsetToVisualPosition(highlightInfo.getEndOffset());
      if (endPosition.line <= targetPosition.line) return targetPosition;
      Point targetPoint = editor.visualPositionToXY(targetPosition);
      Point endPoint = editor.visualPositionToXY(endPosition);
      Point resultPoint = new Point(targetPoint.x, endPoint.x > targetPoint.x ? endPoint.y : editor.visualLineToY(endPosition.line - 1));
      return editor.xyToVisualPosition(resultPoint);
    }

    @Nullable EditorHoverInfo calcInfo(@NotNull Editor editor) {
      HighlightHoverInfo highlightHoverInfo = HighlightHoverInfo.highlightHoverInfo(editor, getHighlightInfo());
      DocumentationHoverInfo documentationHoverInfo = documentationHoverInfo(editor);
      return highlightHoverInfo == null && documentationHoverInfo == null
             ? null
             : new EditorHoverInfo(highlightHoverInfo, documentationHoverInfo);
    }

    private @Nullable DocumentationHoverInfo documentationHoverInfo(@NotNull Editor editor) {
      try {
        return showDocumentation() && EditorSettingsExternalizable.getInstance().isShowQuickDocOnMouseOverElement()
          ? calcTargetDocumentationInfo(Objects.requireNonNull(editor.getProject()), editor, targetOffset)
          : null;
      }
      catch (IndexNotReadyException ignored) {
        return null;
      }
    }

    private enum Relation {
      SAME, // no need to update popup
      SIMILAR, // popup needs to be updated
      DIFFERENT // popup needs to be closed, and the new one shown
    }
  }

  public static @NotNull EditorMouseHoverPopupManager getInstance() {
    return ApplicationManager.getApplication().getService(EditorMouseHoverPopupManager.class);
  }

  /**
   * @deprecated Returns `null` in v2 implementation.
   */
  @Deprecated
  public @Nullable DocumentationComponent getDocumentationComponent() {
    AbstractPopup hint = getCurrentHint();
    return hint == null ? null : UIUtil.findComponentOfType(hint.getComponent(), DocumentationComponent.class);
  }

  static final class MyEditorMouseMotionEventListener implements EditorMouseMotionListener {
    @Override
    public void mouseMoved(@NotNull EditorMouseEvent e) {
      getInstance().handleMouseMoved(e);
    }
  }

  static final class MyEditorMouseEventListener implements EditorMouseListener {
    @Override
    public void mouseEntered(@NotNull EditorMouseEvent event) {
      // we receive MOUSE_MOVED event after MOUSE_ENTERED even if the mouse wasn't physically moved,
      // e.g., if a popup overlapping editor has been closed
      getInstance().skipNextMovement();
    }

    @Override
    public void mouseExited(@NotNull EditorMouseEvent event) {
      getInstance().cancelCurrentProcessing();
    }

    @Override
    public void mousePressed(@NotNull EditorMouseEvent event) {
      getInstance().cancelProcessingAndCloseHint();
    }
  }

  private static final class MyActionListener implements AnActionListener {
    @Override
    public void beforeActionPerformed(@NotNull AnAction action, @NotNull AnActionEvent event) {
      if (action instanceof HintManagerImpl.ActionToIgnore) {
        return;
      }
      AbstractPopup currentHint = getInstance().getCurrentHint();
      if (currentHint != null) {
        Component contextComponent = event.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT);
        JBPopup contextPopup = PopupUtil.getPopupContainerFor(contextComponent);
        if (contextPopup == currentHint) {
          return;
        }
      }
      getInstance().cancelProcessingAndCloseHint();
    }

    @Override
    public void beforeEditorTyping(char c, @NotNull DataContext dataContext) {
      getInstance().cancelProcessingAndCloseHint();
    }
  }
}
