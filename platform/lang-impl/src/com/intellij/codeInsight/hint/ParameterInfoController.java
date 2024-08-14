// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hint;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.daemon.impl.ParameterHintsPresentationManager;
import com.intellij.codeInsight.lookup.LookupEvent;
import com.intellij.codeInsight.lookup.LookupListener;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.LookupManagerListener;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.codeWithMe.ClientId;
import com.intellij.ide.IdeTooltip;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.lang.parameterInfo.ParameterInfoHandler;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteIntentReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.Balloon.Position;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageEditorUtil;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.HintHint;
import com.intellij.ui.LightweightHint;
import com.intellij.ui.ScreenUtil;
import com.intellij.util.indexing.DumbModeAccessType;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.List;

import static com.intellij.codeInsight.hint.ParameterInfoTaskRunnerUtil.runTask;

public final class ParameterInfoController extends ParameterInfoControllerBase {
  private LightweightHint myHint;
  private final ParameterInfoComponent myComponent;
  private boolean myKeepOnHintHidden;

  private final MyBestLocationPointProvider myProvider;

  private Runnable myLateShowHintCallback;

  @Override
  protected boolean canBeDisposed() {
    return myLateShowHintCallback == null && !myHint.isVisible() && !myKeepOnHintHidden && !ApplicationManager.getApplication().isHeadlessEnvironment()
           || myEditor instanceof EditorWindow && !((EditorWindow)myEditor).isValid();
  }

  @Override
  public boolean isHintShown(boolean anyType) {
    return myHint.isVisible() && (!mySingleParameterInfo || anyType);
  }

  public ParameterInfoController(@NotNull Project project,
                                 @NotNull Editor editor,
                                 int lbraceOffset,
                                 Object[] descriptors,
                                 Object highlighted,
                                 PsiElement parameterOwner,
                                 @NotNull ParameterInfoHandler handler,
                                 boolean showHint,
                                 boolean requestFocus) {
    super(project, editor, lbraceOffset, descriptors, highlighted, parameterOwner, handler, showHint);
    myProvider = new MyBestLocationPointProvider(editor);
    myComponent = new ParameterInfoComponent(myParameterInfoControllerData, editor, requestFocus, true);
    myHint = createHint();
    myKeepOnHintHidden = !showHint;

    myHint.setSelectingHint(true);
    myParameterInfoControllerData.setParameterOwner(parameterOwner);
    myParameterInfoControllerData.setHighlighted(highlighted);

    registerSelf();
    setupListeners();

    LookupListener lookupListener = new LookupListener() {
      LookupImpl activeLookup = null;
      final MergingUpdateQueue queue = new MergingUpdateQueue("Update parameter info position", 200, true, myComponent);

      @Override
      public void lookupShown(@NotNull LookupEvent event) {
        activeLookup = (LookupImpl)event.getLookup();
      }

      @Override
      public void lookupCanceled(@NotNull LookupEvent event) {
        activeLookup = null;
      }

      @Override
      public void uiRefreshed() {
        queue.queue(new Update("PI update") {
          @Override
          public void run() {
            if (activeLookup != null) {
              WriteIntentReadAction.run((Runnable)ParameterInfoController.this::updateComponent);
            }
          }
        });
      }
    };


    LookupManagerListener lookupManagerListener = (oldLookup, newLookup) -> {
      if (newLookup != null && ClientId.isCurrentlyUnderLocalId()) {
        newLookup.addLookupListener(lookupListener);
      }
    };

    project.getMessageBus().connect(this).subscribe(LookupManagerListener.TOPIC, lookupManagerListener);

    if (showHint) {
      showHint(requestFocus, mySingleParameterInfo);
    }
    else {
      updateComponent();
    }
  }

  @Override
  public void setDescriptors(Object[] descriptors) {
    super.setDescriptors(descriptors);
    myComponent.fireDescriptorsWereSet();
  }

  @Override
  protected @NotNull ParameterInfoControllerData createParameterInfoControllerData(@NotNull ParameterInfoHandler<PsiElement, Object> handler) {
    return new ParameterInfoControllerData(handler) {

      @Override
      public boolean isDescriptorEnabled(int descriptorIndex) {
        return myComponent.isEnabled(descriptorIndex);
      }

      @Override
      public void setDescriptorEnabled(int descriptorIndex, boolean enabled) {
        myComponent.setEnabled(descriptorIndex, enabled);
      }
    };
  }

  private LightweightHint createHint() {
    JPanel wrapper = new WrapperPanel();
    wrapper.add(myComponent);
    return new LightweightHint(wrapper);
  }

  @Override
  public void showHint(boolean requestFocus, boolean singleParameterInfo) {
    if (myHint.isVisible()) {
      JComponent myHintComponent = myHint.getComponent();
      myHintComponent.removeAll();
      hideHint();
      myHint = createHint();
    }

    mySingleParameterInfo = singleParameterInfo && myKeepOnHintHidden;

    int caretOffset = myEditor.getCaretModel().getOffset();
    Pair<Point, Short> pos = myProvider.getBestPointPosition(myHint, myParameterInfoControllerData.getParameterOwner(), caretOffset,
                                                             null, HintManager.ABOVE);
    @SuppressWarnings("MagicConstant")
    HintHint hintHint = HintManagerImpl.createHintHint(myEditor, pos.getFirst(), myHint, pos.getSecond());
    hintHint.setExplicitClose(true);
    hintHint.setRequestFocus(requestFocus);
    hintHint.setShowImmediately(true);
    if (!ExperimentalUI.isNewUI()) {
      hintHint.setBorderColor(ParameterInfoComponent.BORDER_COLOR);
      hintHint.setBorderInsets(JBUI.insets(4, 1, 4, 1));
      hintHint.setComponentBorder(JBUI.Borders.empty());
    }
    else {
      hintHint.setBorderInsets(JBUI.insets(8, 8, 10, 8));
    }

    int flags = HintManager.HIDE_BY_ESCAPE | HintManager.UPDATE_BY_SCROLLING;
    if (!singleParameterInfo && myKeepOnHintHidden) flags |= HintManager.HIDE_BY_TEXT_CHANGE;
    int finalFlags = flags;

    Editor editorToShow = InjectedLanguageEditorUtil.getTopLevelEditor(myEditor);

    //update presentation of descriptors synchronously
    myComponent.update(mySingleParameterInfo);

    // is case of injection we need to calculate position for EditorWindow
    // also we need to show the hint in the main editor because of intention bulb
    Runnable showHintCallback =
      () -> HintManagerImpl.getInstanceImpl().showEditorHint(myHint, editorToShow, pos.getFirst(), finalFlags, 0, false, hintHint);
    if (myComponent.isSetup()) {
      showHintCallback.run();
      myLateShowHintCallback = null;
    }
    else {
      myLateShowHintCallback = showHintCallback;
    }

    updateComponent();
  }

  @Override
  public void updateComponent() {
    if (canBeDisposed()) {
      Disposer.dispose(this);
      return;
    }

    PsiFile file = PsiUtilBase.getPsiFileInEditor(myEditor, myProject);
    int caretOffset = myEditor.getCaretModel().getOffset();
    int offset = getCurrentOffset();
    UpdateParameterInfoContextBase context = new UpdateParameterInfoContextBase(offset, file);
    executeFindElementForUpdatingParameterInfo(context, elementForUpdating -> {
      myParameterInfoControllerData.getHandler().processFoundElementForUpdatingParameterInfo(elementForUpdating, context);
      if (elementForUpdating != null) {
        executeUpdateParameterInfo(elementForUpdating, context, () -> {
          boolean knownParameter = (myParameterInfoControllerData.getDescriptors().length == 1 ||
                                    myParameterInfoControllerData.getHighlighted() != null) &&
                                   myParameterInfoControllerData.getCurrentParameterIndex() != -1;
          if (mySingleParameterInfo && !knownParameter && myHint.isVisible()) {
            hideHint();
          }
          if (myKeepOnHintHidden && knownParameter && !myHint.isVisible()) {
            AutoPopupController.getInstance(myProject).autoPopupParameterInfo(myEditor, null);
          }
          if (!myDisposed && ((myHint.isVisible() || myLateShowHintCallback != null) && !myEditor.isDisposed() &&
                              (myEditor.getComponent().getRootPane() != null || ApplicationManager.getApplication().isUnitTestMode()) ||
                              ApplicationManager.getApplication().isHeadlessEnvironment())) {
            Model result = myComponent.update(mySingleParameterInfo);
            if (myLateShowHintCallback != null) {
              Runnable showHintCallback = myLateShowHintCallback;
              myLateShowHintCallback = null;
              showHintCallback.run();
            }
            result.project = myProject;
            result.range = myParameterInfoControllerData.getParameterOwner().getTextRange();
            result.editor = myEditor;
            for (ParameterInfoListener listener : ParameterInfoListener.EP_NAME.getExtensionList()) {
              listener.hintUpdated(result);
            }
            if (ApplicationManager.getApplication().isHeadlessEnvironment()) return;
            IdeTooltip tooltip = myHint.getCurrentIdeTooltip();
            short position = tooltip != null
                             ? toShort(tooltip.getPreferredPosition())
                             : HintManager.ABOVE;
            Pair<Point, Short> pos = myProvider.getBestPointPosition(
              myHint, elementForUpdating,
              caretOffset, myEditor.getCaretModel().getVisualPosition(), position);

            //noinspection MagicConstant
            HintManagerImpl.adjustEditorHintPosition(myHint, myEditor, pos.getFirst(), pos.getSecond());
          }
        });
      }
      else {
        hideHint();
        if (!myKeepOnHintHidden) {
          Disposer.dispose(this);
        }
      }
    });
  }

  private void executeUpdateParameterInfo(PsiElement elementForUpdating,
                                          UpdateParameterInfoContextBase context,
                                          Runnable continuation) {
    PsiElement parameterOwner = context.getParameterOwner();
    if (parameterOwner != null && !parameterOwner.equals(elementForUpdating)) {
      context.removeHint();
      return;
    }

    runTask(myProject,
            ReadAction.nonBlocking(() -> {
              DumbModeAccessType.RELIABLE_DATA_ONLY.ignoreDumbMode(() -> myParameterInfoControllerData.getHandler().updateParameterInfo(elementForUpdating, context));
              return elementForUpdating;
            })
              .withDocumentsCommitted(myProject)
              .expireWhen(
                () -> !myKeepOnHintHidden && !myHint.isVisible() && myLateShowHintCallback == null && !ApplicationManager.getApplication().isHeadlessEnvironment() ||
                      getCurrentOffset() != context.getOffset() ||
                      !elementForUpdating.isValid())
              .expireWith(this),
            element -> {
              if (element != null && continuation != null) {
                context.applyUIChanges();
                continuation.run();
              }
            },
            null,
            myEditor);
  }

  @HintManager.PositionFlags
  private static short toShort(Position position) {
    return switch (position) {
      case above -> HintManager.ABOVE;
      case atLeft -> HintManager.LEFT;
      case atRight -> HintManager.RIGHT;
      default -> HintManager.UNDER;
    };
  }

  @Override
  protected void moveToParameterAtOffset(int offset) {
    PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(myEditor.getDocument());
    PsiElement argsList = findArgumentList(file, offset, -1);
    if (argsList == null && !CodeInsightSettings.getInstance().SHOW_PARAMETER_NAME_HINTS_ON_COMPLETION) return;

    if (!myHint.isVisible()) AutoPopupController.getInstance(myProject).autoPopupParameterInfo(myEditor, null);

    offset = adjustOffsetToInlay(offset);
    myEditor.getCaretModel().moveToOffset(offset);
    myEditor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    myEditor.getSelectionModel().removeSelection();
    if (argsList != null) {
      executeUpdateParameterInfo(argsList, new UpdateParameterInfoContextBase(offset, file), null);
    }
  }

  private int adjustOffsetToInlay(int offset) {
    CharSequence text = myEditor.getDocument().getImmutableCharSequence();
    int hostWhitespaceStart = CharArrayUtil.shiftBackward(text, offset, WHITESPACE) + 1;
    int hostWhitespaceEnd = CharArrayUtil.shiftForward(text, offset, WHITESPACE);
    Editor hostEditor = myEditor;
    if (myEditor instanceof EditorWindow) {
      hostEditor = ((EditorWindow)myEditor).getDelegate();
      hostWhitespaceStart = ((EditorWindow)myEditor).getDocument().injectedToHost(hostWhitespaceStart);
      hostWhitespaceEnd = ((EditorWindow)myEditor).getDocument().injectedToHost(hostWhitespaceEnd);
    }
    List<Inlay<?>> inlays = ParameterHintsPresentationManager.getInstance().getParameterHintsInRange(hostEditor,
                                                                                                  hostWhitespaceStart, hostWhitespaceEnd);
    for (Inlay inlay : inlays) {
      int inlayOffset = inlay.getOffset();
      if (myEditor instanceof EditorWindow) {
        if (((EditorWindow)myEditor).getDocument().getHostRange(inlayOffset) == null) continue;
        inlayOffset = ((EditorWindow)myEditor).getDocument().hostToInjected(inlayOffset);
      }
      return inlayOffset;
    }
    return offset;
  }

  @Override
  public void setPreservedOnHintHidden(boolean value) {
    myKeepOnHintHidden = value;
  }

  @Override
  public boolean isPreservedOnHintHidden() {
    return myKeepOnHintHidden;
  }

  /**
   * Returned Point is in layered pane coordinate system.
   * Second value is a {@link HintManager.PositionFlags position flag}.
   */
  static Pair<Point, Short> chooseBestHintPosition(Editor editor,
                                                   VisualPosition pos,
                                                   LightweightHint hint,
                                                   LookupImpl activeLookup,
                                                   short preferredPosition,
                                                   boolean showLookupHint) {
    if (ApplicationManager.getApplication().isUnitTestMode() ||
        ApplicationManager.getApplication().isHeadlessEnvironment()) {
      return new Pair<>(new Point(), HintManager.DEFAULT);
    }

    HintManagerImpl hintManager = HintManagerImpl.getInstanceImpl();
    Dimension hintSize = hint.getComponent().getPreferredSize();
    JComponent editorComponent = editor.getComponent();
    JLayeredPane layeredPane = editorComponent.getRootPane().getLayeredPane();

    Point p1;
    Point p2;
    if (showLookupHint) {
      p1 = hintManager.getHintPosition(hint, editor, HintManager.UNDER);
      p2 = hintManager.getHintPosition(hint, editor, HintManager.ABOVE);
    }
    else {
      p1 = HintManagerImpl.getHintPosition(hint, editor, pos, HintManager.UNDER);
      p2 = HintManagerImpl.getHintPosition(hint, editor, pos, HintManager.ABOVE);
    }

    boolean isRealPopup = hint.isRealPopup();

    boolean p1Ok, p2Ok;


    if (!showLookupHint && activeLookup != null && activeLookup.isShown()) {
      Rectangle lookupBounds = activeLookup.getBounds();

      p1Ok = p1.y + hintSize.height + 50 < layeredPane.getHeight() && !isHintIntersectWithLookup(p1, hintSize, lookupBounds, isRealPopup, HintManager.UNDER);
      p2Ok = p2.y - hintSize.height - 70 >= 0 && !isHintIntersectWithLookup(p2, hintSize, lookupBounds, isRealPopup, HintManager.ABOVE);

      if (activeLookup.isPositionedAboveCaret()) {
        if (!p1Ok) {
          var abovePoint = new Point(lookupBounds.x, lookupBounds.y - hintSize.height - 10);
          SwingUtilities.convertPointToScreen(abovePoint, layeredPane);
          abovePoint.move(lookupBounds.x, lookupBounds.y - hintSize.height - 10);
          hint.setForceShowAsPopup(true);
          return new Pair<>(abovePoint, HintManager.DEFAULT);
        }
      }
      else {
        if (!p2Ok) {
          var underPoint = new Point(lookupBounds.x, lookupBounds.y + lookupBounds.height + 10);
          SwingUtilities.convertPointToScreen(underPoint, layeredPane);
          var screenRectangle = new Rectangle(underPoint, hintSize);
          if (isFitTheScreen(screenRectangle)) {
            // calculate if hint can be shown under lookup
            underPoint.move(lookupBounds.x, lookupBounds.y + lookupBounds.height + 10);
            hint.setForceShowAsPopup(true);
            return new Pair<>(underPoint, HintManager.DEFAULT);
          }
          else {
            hint.setForceShowAsPopup(true);
            var abovePoint = new Point(p2.x - hintSize.width / 2, p2.y - hintSize.height);
            return new Pair<>(abovePoint, HintManager.ABOVE);
          }
        }
      }
    }
    else {
      p1Ok = p1.y + hintSize.height < layeredPane.getHeight();
      p2Ok = p2.y >= 0;
    }

    if (isRealPopup) {
      hint.setForceShowAsPopup(false);
    }


    if (!showLookupHint) {
      if (preferredPosition != HintManager.DEFAULT) {
        if (preferredPosition == HintManager.ABOVE) {
          if (p2Ok) return new Pair<>(p2, HintManager.ABOVE);
        }
        else if (preferredPosition == HintManager.UNDER) {
          if (p1Ok) return new Pair<>(p1, HintManager.UNDER);
        }
      }
    }
    if (p1Ok) return new Pair<>(p1, HintManager.UNDER);
    if (p2Ok) return new Pair<>(p2, HintManager.ABOVE);

    int underSpace = layeredPane.getHeight() - p1.y;
    int aboveSpace = p2.y;
    return aboveSpace > underSpace ? new Pair<>(new Point(p2.x, 0), HintManager.UNDER) : new Pair<>(p1,
                                                                                                    HintManager.ABOVE);
  }

  private static boolean isFitTheScreen(Rectangle aRectangle) {
    int screenX = aRectangle.x + aRectangle.width / 2;
    int screenY = aRectangle.y + aRectangle.height / 2;
    Rectangle screen = ScreenUtil.getScreenRectangle(screenX, screenY);
    return screen.contains(aRectangle);
  }

  private static boolean isHintIntersectWithLookup(Point hintPoint,
                                                   Dimension hintSize,
                                                   Rectangle lookupBounds,
                                                   boolean isRealPopup,
                                                   short hintPosition){
    Point leftTopPoint = isRealPopup
      ? hintPoint
      : hintPosition == HintManager.ABOVE
          ? new Point(hintPoint.x - hintSize.width / 2, hintPoint.y - hintSize.height)
          : new Point(hintPoint.x - hintSize.width / 2, hintPoint.y);

    return lookupBounds.intersects(new Rectangle(leftTopPoint, hintSize));
  }

  @Override
  protected void hideHint() {
    myLateShowHintCallback = null;
    myHint.hide();
    for (ParameterInfoListener listener : ParameterInfoListener.EP_NAME.getExtensionList()) {
      listener.hintHidden(myProject);
    }
  }

  private static final class MyBestLocationPointProvider {
    private final Editor myEditor;
    private int previousOffset = -1;
    private Rectangle previousLookupBounds;
    private Dimension previousHintSize;
    private Point previousBestPoint;
    private Short previousBestPosition;

    MyBestLocationPointProvider(Editor editor) {
      myEditor = editor;
    }

    private @NotNull Pair<Point, Short> getBestPointPosition(LightweightHint hint,
                                                             PsiElement list,
                                                             int offset,
                                                             VisualPosition pos,
                                                             short preferredPosition) {
      if (list != null) {
        TextRange range = list.getTextRange();
        TextRange rangeWithoutParens = TextRange.from(range.getStartOffset() + 1, Math.max(range.getLength() - 2, 0));
        if (!rangeWithoutParens.contains(offset)) {
          offset = offset < rangeWithoutParens.getStartOffset() ? rangeWithoutParens.getStartOffset() : rangeWithoutParens.getEndOffset();
          pos = null;
        }
      }

      LookupImpl activeLookup = (LookupImpl)LookupManager.getActiveLookup(myEditor);
      Rectangle lookupBounds = !ApplicationManager.getApplication().isUnitTestMode()
                               && !ApplicationManager.getApplication().isHeadlessEnvironment()
                               && activeLookup != null
                               && activeLookup.isShown()
                               ? activeLookup.getBounds()
                               : null;

      Dimension hintSize = hint.getSize();

      boolean lookupPositionChanged = lookupBounds != null && !lookupBounds.equals(previousLookupBounds);
      boolean hintSizeChanged = !hintSize.equals(previousHintSize);

      if (previousOffset == offset && !lookupPositionChanged && !hintSizeChanged) {
        return Pair.create(previousBestPoint, previousBestPosition);
      }

      Editor editor = myEditor;
      if (pos == null) {
        pos = EditorUtil.inlayAwareOffsetToVisualPosition(myEditor, offset);
        // The position above is always in the host editor. If we are in an injected
        // editor this position will likely be outside of our range and the hint position
        // will be our range's end. To avoid that and compute hint position correctly,
        // switch to the host editor.
        editor = myEditor instanceof EditorWindow ? ((EditorWindow)myEditor).getDelegate() : editor;
      }
      Pair<Point, Short> position = chooseBestHintPosition(editor, pos, hint, activeLookup, preferredPosition, false);

      previousBestPoint = position.getFirst();
      previousBestPosition = position.getSecond();
      previousOffset = offset;
      previousLookupBounds = lookupBounds;
      previousHintSize = hintSize;
      return position;
    }
  }

  static final class WrapperPanel extends JPanel {
    WrapperPanel() {
      super(new BorderLayout());
      setBorder(JBUI.Borders.empty());
      setOpaque(!ExperimentalUI.isNewUI());
    }

    // foreground/background/font are used to style the popup (HintManagerImpl.createHintHint)
    @Override
    public Color getForeground() {
      return getComponentCount() == 0 ? super.getForeground() : getComponent(0).getForeground();
    }

    @Override
    public Color getBackground() {
      return getComponentCount() == 0 || ExperimentalUI.isNewUI() ? super.getBackground() : getComponent(0).getBackground();
    }

    @Override
    public Font getFont() {
      return getComponentCount() == 0 ? super.getFont() : getComponent(0).getFont();
    }

    // for test purposes
    @Override
    public String toString() {
      return getComponentCount() == 0 ? "<empty>" : getComponent(0).toString();
    }
  }
}
