/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

package com.intellij.codeInsight.hint;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.daemon.impl.ParameterHintsPresentationManager;
import com.intellij.codeInsight.hints.ParameterHintsPassFactory;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.ide.IdeTooltip;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.lang.parameterInfo.ParameterInfoHandler;
import com.intellij.lang.parameterInfo.ParameterInfoHandlerWithTabActionSupport;
import com.intellij.lang.parameterInfo.ParameterInfoUtils;
import com.intellij.lang.parameterInfo.UpdateParameterInfoContext;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.Balloon.Position;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.ui.HintHint;
import com.intellij.ui.LightweightHint;
import com.intellij.util.Alarm;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.LockSupport;

public class ParameterInfoController implements Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.hint.ParameterInfoController");
  private final Project myProject;
  @NotNull private final Editor myEditor;

  private final RangeMarker myLbraceMarker;
  private final LightweightHint myHint;
  private final ParameterInfoComponent myComponent;
  private final boolean myKeepOnHintHidden;

  private final CaretListener myEditorCaretListener;
  @NotNull private final ParameterInfoHandler<Object, Object> myHandler;
  private final MyBestLocationPointProvider myProvider;

  private final Alarm myAlarm = new Alarm();
  private static final int DELAY = 200;

  private boolean mySingleParameterInfo;
  private boolean myDisposed;

  /**
   * Keeps Vector of ParameterInfoController's in Editor
   */
  private static final Key<List<ParameterInfoController>> ALL_CONTROLLERS_KEY = Key.create("ParameterInfoController.ALL_CONTROLLERS_KEY");

  public static ParameterInfoController findControllerAtOffset(Editor editor, int offset) {
    List<ParameterInfoController> allControllers = getAllControllers(editor);
    for (int i = 0; i < allControllers.size(); ++i) {
      ParameterInfoController controller = allControllers.get(i);

      if (controller.myLbraceMarker.getStartOffset() == offset) {
        if (controller.myKeepOnHintHidden || controller.myHint.isVisible()) return controller;
        Disposer.dispose(controller);
        --i;
      }
    }

    return null;
  }

  private static List<ParameterInfoController> getAllControllers(@NotNull Editor editor) {
    List<ParameterInfoController> array = editor.getUserData(ALL_CONTROLLERS_KEY);
    if (array == null){
      array = new ArrayList<>();
      editor.putUserData(ALL_CONTROLLERS_KEY, array);
    }
    return array;
  }

  public static boolean existsForEditor(@NotNull Editor editor) {
    return !getAllControllers(editor).isEmpty();
  }

  public static boolean isAlreadyShown(Editor editor, int lbraceOffset, boolean singleParameterInfo) {
    ParameterInfoController controller = findControllerAtOffset(editor, lbraceOffset);
    return controller != null && controller.myHint.isVisible() && (!controller.mySingleParameterInfo || !singleParameterInfo);
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
    myProject = project;
    myEditor = editor;
    myHandler = handler;
    myProvider = new MyBestLocationPointProvider(editor);
    myLbraceMarker = editor.getDocument().createRangeMarker(lbraceOffset, lbraceOffset);
    myComponent = new ParameterInfoComponent(descriptors, editor, handler, requestFocus);
    myHint = new LightweightHint(myComponent);
    myKeepOnHintHidden = !showHint;
    mySingleParameterInfo = !showHint;

    myHint.setSelectingHint(true);
    myComponent.setParameterOwner(parameterOwner);
    myComponent.setHighlightedParameter(highlighted);

    List<ParameterInfoController> allControllers = getAllControllers(myEditor);
    allControllers.add(this);

    myEditorCaretListener = new CaretListener(){
      @Override
      public void caretPositionChanged(CaretEvent e) {
        rescheduleUpdate();
      }
    };
    myEditor.getCaretModel().addCaretListener(myEditorCaretListener);

    myEditor.getDocument().addDocumentListener(new DocumentListener() {
      @Override
      public void documentChanged(DocumentEvent e) {
        rescheduleUpdate();
      }
    }, this);

    MessageBusConnection connection = project.getMessageBus().connect(this);
    connection.subscribe(ExternalParameterInfoChangesProvider.TOPIC, (e, offset) -> {
      if (e != myEditor || myLbraceMarker.getStartOffset() != offset) return;
      rescheduleUpdate();
    });

    PropertyChangeListener lookupListener = new PropertyChangeListener() {
      @Override
      public void propertyChange(PropertyChangeEvent evt) {
        if (LookupManager.PROP_ACTIVE_LOOKUP.equals(evt.getPropertyName())) {
          Lookup lookup = (Lookup)evt.getNewValue();
          if (lookup != null) {
            adjustPositionForLookup(lookup);
          }
        }
      }
    };
    LookupManager.getInstance(project).addPropertyChangeListener(lookupListener, this);
    if (myEditor instanceof EditorImpl) {
      Disposer.register(((EditorImpl)myEditor).getDisposable(), this);
    }

    myComponent.update(mySingleParameterInfo); // to have correct preferred size
    if (showHint) {
      showHint(requestFocus, mySingleParameterInfo);
    }
    updateComponent();
  }

  @Override
  public void dispose(){
    if (myDisposed) return;
    myDisposed = true;
    myHint.hide();
    myHandler.dispose();
    List<ParameterInfoController> allControllers = getAllControllers(myEditor);
    allControllers.remove(this);
    myEditor.getCaretModel().removeCaretListener(myEditorCaretListener);
  }

  public void showHint(boolean requestFocus, boolean singleParameterInfo) {
    mySingleParameterInfo = singleParameterInfo;
    
    Pair<Point, Short> pos = myProvider.getBestPointPosition(myHint, myComponent.getParameterOwner(), myLbraceMarker.getStartOffset(), true, HintManager.UNDER);
    HintHint hintHint = HintManagerImpl.createHintHint(myEditor, pos.getFirst(), myHint, pos.getSecond());
    hintHint.setExplicitClose(true);
    hintHint.setRequestFocus(requestFocus);

    Editor editorToShow = myEditor instanceof EditorWindow ? ((EditorWindow)myEditor).getDelegate() : myEditor;
    // is case of injection we need to calculate position for EditorWindow
    // also we need to show the hint in the main editor because of intention bulb
    HintManagerImpl.getInstanceImpl().showEditorHint(myHint, editorToShow, pos.getFirst(), HintManager.HIDE_BY_ESCAPE | HintManager.UPDATE_BY_SCROLLING, 0, false, hintHint);

    updateComponent();
  }

  private void adjustPositionForLookup(@NotNull Lookup lookup) {
    if (myEditor.isDisposed()) {
      Disposer.dispose(this);
      return;
    }

    if (!myHint.isVisible()) {
      if (!myKeepOnHintHidden) Disposer.dispose(this);
      return;
    }

    IdeTooltip tooltip = myHint.getCurrentIdeTooltip();
    if (tooltip != null) {
      JRootPane root = myEditor.getComponent().getRootPane();
      if (root != null) {
        Point p = tooltip.getShowingPoint().getPoint(root.getLayeredPane());
        if (lookup.isPositionedAboveCaret()) {
          if (Position.above == tooltip.getPreferredPosition()) {
            myHint.pack();
            myHint.updatePosition(Position.below);
            myHint.updateLocation(p.x, p.y + tooltip.getPositionChangeY());
          }
        }
        else {
          if (Position.below == tooltip.getPreferredPosition()) {
            myHint.pack();
            myHint.updatePosition(Position.above);
            myHint.updateLocation(p.x, p.y - tooltip.getPositionChangeY());
          }
        }
      }
    }
  }

  private void rescheduleUpdate(){
    myAlarm.cancelAllRequests();
    Runnable request = () -> {
      if (!myDisposed && !myProject.isDisposed()) {
        PsiDocumentManager.getInstance(myProject).performLaterWhenAllCommitted(() -> {
          try {
            DumbService.getInstance(myProject).withAlternativeResolveEnabled(this::updateComponent);
          }
          catch (IndexNotReadyException e) {
            LOG.info(e);
            Disposer.dispose(this);
          }
        });
      }
    };
    myAlarm.addRequest(request, DELAY, ModalityState.stateForComponent(myEditor.getComponent()));
  }

  public void updateComponent(){
    if (myKeepOnHintHidden) {
      boolean removeHints = true;
      PsiElement owner = myComponent.getParameterOwner();
      if (owner != null && owner.isValid()) {
        int caretOffset = myEditor.getCaretModel().getOffset();
        TextRange ownerTextRange = owner.getTextRange();
        if (ownerTextRange != null) {
          if (caretOffset >= ownerTextRange.getStartOffset() && caretOffset <= ownerTextRange.getEndOffset()) {
            removeHints = false;
          }
          else {
            for (PsiElement element : owner.getChildren()) {
              if (element instanceof PsiErrorElement) {
                removeHints = false;
                break;
              }
            }
          }
        }
      }
      if (removeHints) {
        ParameterHintsPassFactory.forceHintsUpdateOnNextPass(myEditor);
        Disposer.dispose(this);
        return;
      }
    }

    if (!myHint.isVisible() && !myKeepOnHintHidden && !ApplicationManager.getApplication().isUnitTestMode()) {
      Disposer.dispose(this);
      return;
    }

    final PsiFile file =  PsiUtilBase.getPsiFileInEditor(myEditor, myProject);
    CharSequence chars = myEditor.getDocument().getCharsSequence();
    boolean noDelimiter = myHandler instanceof ParameterInfoHandlerWithTabActionSupport &&
                          ((ParameterInfoHandlerWithTabActionSupport)myHandler).getActualParameterDelimiterType() == TokenType.WHITE_SPACE;
    int caretOffset = myEditor.getCaretModel().getOffset();
    final int offset = noDelimiter ? caretOffset :
                       CharArrayUtil.shiftBackward(chars, caretOffset - 1, " \t") + 1;

    final UpdateParameterInfoContext context = new MyUpdateParameterInfoContext(offset, file);
    final Object elementForUpdating = myHandler.findElementForUpdatingParameterInfo(context);

    if (elementForUpdating != null) {
      myHandler.updateParameterInfo(elementForUpdating, context);
      if (!myDisposed && myHint.isVisible() && !myEditor.isDisposed() &&
          myEditor.getComponent().getRootPane() != null) {
        myComponent.update(mySingleParameterInfo);
        IdeTooltip tooltip = myHint.getCurrentIdeTooltip();
        short position = tooltip != null
                         ? toShort(tooltip.getPreferredPosition())
                         : HintManager.UNDER;
        Pair<Point, Short> pos = myProvider.getBestPointPosition(
          myHint, elementForUpdating instanceof PsiElement ? (PsiElement)elementForUpdating : null,
          caretOffset, true, position);
        HintManagerImpl.adjustEditorHintPosition(myHint, myEditor, pos.getFirst(), pos.getSecond());
      }
    }
    else {
      myHint.hide();
      if (!myKeepOnHintHidden) {
        Disposer.dispose(this);
      }
    }
  }

  @HintManager.PositionFlags
  private static short toShort(Position position) {
    switch (position) {
      case above:
        return HintManager.ABOVE;
      case atLeft:
        return HintManager.LEFT;
      case atRight:
        return HintManager.RIGHT;
      default:
        return HintManager.UNDER;
    }
  }

  public static boolean hasPrevOrNextParameter(Editor editor, int lbraceOffset, boolean isNext) {
    ParameterInfoController controller = findControllerAtOffset(editor, lbraceOffset);
    return controller != null && controller.getPrevOrNextParameterOffset(isNext) != -1;
  }

  public static void prevOrNextParameter(Editor editor, int lbraceOffset, boolean isNext) {
    ParameterInfoController controller = findControllerAtOffset(editor, lbraceOffset);
    int newOffset = controller != null ? controller.getPrevOrNextParameterOffset(isNext) : -1;
    if (newOffset != -1) {
      controller.moveToParameterAtOffset(newOffset);
    }
  }

  private void moveToParameterAtOffset(int offset) {
    PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(myEditor.getDocument());
    PsiElement argsList = findArgumentList(file, offset, -1);
    if (argsList == null && !areParametersHintsEnabledOnCompletion()) return;

    if (!myHint.isVisible()) AutoPopupController.getInstance(myProject).autoPopupParameterInfo(myEditor, null);
    
    offset = adjustOffsetToInlay(offset);
    VisualPosition visualPosition = myEditor.offsetToVisualPosition(offset);
    if (myEditor.getInlayModel().hasInlineElementAt(visualPosition)) {
      visualPosition = new VisualPosition(visualPosition.line, visualPosition.column + 1);
    }
    myEditor.getCaretModel().moveToVisualPosition(visualPosition);
    myEditor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    myEditor.getSelectionModel().removeSelection();
    if (argsList != null) {
      myHandler.updateParameterInfo(argsList, new MyUpdateParameterInfoContext(offset, file));
    }
  }

  private int adjustOffsetToInlay(int offset) {
    CharSequence text = myEditor.getDocument().getImmutableCharSequence();
    String whitespaceChars = " \t";
    int whitespaceStart = CharArrayUtil.shiftBackward(text, offset, whitespaceChars) + 1;
    int whitespaceEnd = CharArrayUtil.shiftForward(text, offset, whitespaceChars);
    List<Inlay> inlays = myEditor.getInlayModel().getInlineElementsInRange(whitespaceStart, whitespaceEnd);
    for (Inlay inlay : inlays) {
      if (ParameterHintsPresentationManager.getInstance().isParameterHint(inlay)) return inlay.getOffset();
    }
    return offset;
  }

  private int getPrevOrNextParameterOffset(boolean isNext) {
    if (!(myHandler instanceof ParameterInfoHandlerWithTabActionSupport)) return -1;
    ParameterInfoHandlerWithTabActionSupport handler = (ParameterInfoHandlerWithTabActionSupport)myHandler;

    boolean noDelimiter = handler.getActualParameterDelimiterType() == TokenType.WHITE_SPACE;
    int caretOffset = myEditor.getCaretModel().getOffset();
    int offset = noDelimiter ? caretOffset : CharArrayUtil.shiftBackward(myEditor.getDocument().getCharsSequence(), caretOffset - 1, " \t") + 1;
    int lbraceOffset = myLbraceMarker.getStartOffset();
    PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(myEditor.getDocument());
    PsiElement argList = lbraceOffset < offset ? findArgumentList(file, offset, lbraceOffset) : null;
    if (argList == null) return -1;

    @SuppressWarnings("unchecked") PsiElement[] parameters = handler.getActualParameters(argList);
    int currentParameterIndex =
      noDelimiter ? JBIterable.of(parameters).indexOf((o) -> o.getTextRange().containsOffset(offset)) :
      ParameterInfoUtils.getCurrentParameterIndex(argList.getNode(), offset, handler.getActualParameterDelimiterType());
    if (areParametersHintsEnabledOnCompletion()) {
      if (currentParameterIndex < 0 || currentParameterIndex >= parameters.length) return -1;
      if (offset >= argList.getTextRange().getEndOffset()) currentParameterIndex = isNext ? -1 : parameters.length;
      int prevOrNextParameterIndex = currentParameterIndex + (isNext ? 1 : -1);
      if (prevOrNextParameterIndex < 0 || prevOrNextParameterIndex >= parameters.length) {
        PsiElement parameterOwner = myComponent.getParameterOwner();
        return (parameterOwner != null && parameterOwner.isValid()) ? parameterOwner.getTextRange().getEndOffset() : -1;
      }
      else {
        int startOffset = parameters[prevOrNextParameterIndex].getTextRange().getStartOffset();
        return CharArrayUtil.shiftForward(myEditor.getDocument().getImmutableCharSequence(), startOffset, " \t");
      }
    }
    else {
      int prevOrNextParameterIndex = isNext && currentParameterIndex < parameters.length - 1 ? currentParameterIndex + 1 :
                                     !isNext && currentParameterIndex > 0 ? currentParameterIndex - 1 : -1;
      return prevOrNextParameterIndex != -1 ? parameters[prevOrNextParameterIndex].getTextRange().getStartOffset() : -1;
    }
  }

  @Nullable
  public static <E extends PsiElement> E findArgumentList(PsiFile file, int offset, int lbraceOffset){
    if (file == null) return null;
    ParameterInfoHandler[] handlers = ShowParameterInfoHandler.getHandlers(file.getProject(), PsiUtilCore.getLanguageAtOffset(file, offset), file.getViewProvider().getBaseLanguage());

    if (handlers != null) {
      for(ParameterInfoHandler handler:handlers) {
        if (handler instanceof ParameterInfoHandlerWithTabActionSupport) {
          final ParameterInfoHandlerWithTabActionSupport parameterInfoHandler2 = (ParameterInfoHandlerWithTabActionSupport)handler;

          // please don't remove typecast in the following line; it's required to compile the code under old JDK 6 versions
          final E e = (E) ParameterInfoUtils.findArgumentList(file, offset, lbraceOffset, parameterInfoHandler2);
          if (e != null) return e;
        }
      }
    }

    return null;
  }

  public Object[] getObjects() {
    return myComponent.getObjects();
  }

  public Object getHighlighted() {
    return myComponent.getHighlighted();
  }

  public void resetHighlighted() {
    myComponent.setHighlightedParameter(null);
  }

  @TestOnly
  public static void waitForDelayedActions(@NotNull Editor editor, long timeout, @NotNull TimeUnit unit) throws TimeoutException {
    long deadline = System.currentTimeMillis() + unit.toMillis(timeout);
    while (System.currentTimeMillis() < deadline) {
      List<ParameterInfoController> controllers = getAllControllers(editor);
      boolean hasPendingRequests = false;
      for (ParameterInfoController controller : controllers) {
        if (!controller.myAlarm.isEmpty()) {
          hasPendingRequests = true;
          break;
        }
      }
      if (hasPendingRequests) {
        LockSupport.parkNanos(10_000_000);
        UIUtil.dispatchAllInvocationEvents();
      }
      else return;

    }
    throw new TimeoutException();
  }

  /**
   * Returned Point is in layered pane coordinate system.
   * Second value is a {@link com.intellij.codeInsight.hint.HintManager.PositionFlags position flag}.
   */
  static Pair<Point, Short> chooseBestHintPosition(Project project,
                                                   Editor editor,
                                                   LogicalPosition pos,
                                                   LightweightHint hint,
                                                   boolean awtTooltip, short preferredPosition) {
    if (ApplicationManager.getApplication().isUnitTestMode()) return Pair.pair(new Point(), HintManager.DEFAULT);

    HintManagerImpl hintManager = HintManagerImpl.getInstanceImpl();
    Dimension hintSize = hint.getComponent().getPreferredSize();
    JComponent editorComponent = editor.getComponent();
    JLayeredPane layeredPane = editorComponent.getRootPane().getLayeredPane();

    Point p1;
    Point p2;
    boolean isLookupShown = LookupManager.getInstance(project).getActiveLookup() != null;
    if (isLookupShown) {
      p1 = hintManager.getHintPosition(hint, editor, HintManager.UNDER);
      p2 = hintManager.getHintPosition(hint, editor, HintManager.ABOVE);
    }
    else {
      p1 = HintManagerImpl.getHintPosition(hint, editor, pos, HintManager.UNDER);
      p2 = HintManagerImpl.getHintPosition(hint, editor, pos, HintManager.ABOVE);
    }

    if (!awtTooltip) {
      p1.x = Math.min(p1.x, layeredPane.getWidth() - hintSize.width);
      p1.x = Math.max(p1.x, 0);
      p2.x = Math.min(p2.x, layeredPane.getWidth() - hintSize.width);
      p2.x = Math.max(p2.x, 0);
    }

    boolean p1Ok = p1.y + hintSize.height < layeredPane.getHeight();
    boolean p2Ok = p2.y >= 0;

    if (isLookupShown) {
      if (p1Ok) return new Pair<>(p1, HintManager.UNDER);
      if (p2Ok) return new Pair<>(p2, HintManager.ABOVE);
    }
    else {
      if (preferredPosition != HintManager.DEFAULT) {
        if (preferredPosition == HintManager.ABOVE) {
          if (p2Ok) return new Pair<>(p2, HintManager.ABOVE);
        } else if (preferredPosition == HintManager.UNDER) {
          if (p1Ok) return new Pair<>(p1, HintManager.UNDER);
        }
      }

      if (p1Ok) return new Pair<>(p1, HintManager.UNDER);
      if (p2Ok) return new Pair<>(p2, HintManager.ABOVE);
    }

    int underSpace = layeredPane.getHeight() - p1.y;
    int aboveSpace = p2.y;
    return aboveSpace > underSpace ? new Pair<>(new Point(p2.x, 0), HintManager.UNDER) : new Pair<>(p1,
                                                                                                    HintManager.ABOVE);
  }

  public static boolean areParameterTemplatesEnabledOnCompletion() {
    return Registry.is("java.completion.argument.live.template") && !areParametersHintsEnabledInternallyOnCompletion();
  }

  public static boolean areParametersHintsEnabledOnCompletion() {
    return Registry.is("java.completion.argument.hints") && !Registry.is("java.completion.argument.live.template") || 
           areParametersHintsEnabledInternallyOnCompletion();
  }

  private static boolean areParametersHintsEnabledInternallyOnCompletion() {
    return Registry.is("java.completion.argument.hints.internal") && 
           ApplicationManager.getApplication().isInternal() && 
           !ApplicationManager.getApplication().isUnitTestMode();
  }

  public class MyUpdateParameterInfoContext implements UpdateParameterInfoContext {
    private final int myOffset;
    private final PsiFile myFile;

    public MyUpdateParameterInfoContext(final int offset, final PsiFile file) {
      myOffset = offset;
      myFile = file;
    }

    @Override
    public int getParameterListStart() {
      return myLbraceMarker.getStartOffset();
    }

    @Override
    public int getOffset() {
      return myOffset;
    }

    @Override
    public Project getProject() {
      return myProject;
    }

    @Override
    public PsiFile getFile() {
      return myFile;
    }

    @Override
    @NotNull
    public Editor getEditor() {
      return myEditor;
    }

    @Override
    public void removeHint() {
      myHint.hide();
      if (!myKeepOnHintHidden) Disposer.dispose(ParameterInfoController.this);
    }

    @Override
    public void setParameterOwner(final PsiElement o) {
      myComponent.setParameterOwner(o);
    }

    @Override
    public PsiElement getParameterOwner() {
      return myComponent.getParameterOwner();
    }

    @Override
    public void setHighlightedParameter(final Object method) {
      myComponent.setHighlightedParameter(method);
    }

    @Override
    public Object getHighlightedParameter() {
      return myComponent.getHighlighted();
    }

    @Override
    public void setCurrentParameter(final int index) {
      myComponent.setCurrentParameterIndex(index);
    }

    @Override
    public boolean isUIComponentEnabled(int index) {
      return myComponent.isEnabled(index);
    }

    @Override
    public void setUIComponentEnabled(int index, boolean enabled) {
      myComponent.setEnabled(index, enabled);
    }

    @Override
    public Object[] getObjectsToView() {
      return myComponent.getObjects();
    }

    @Override
    public boolean isInnermostContext() {
      PsiElement ourOwner = myComponent.getParameterOwner();
      if (ourOwner == null || !ourOwner.isValid()) return false;
      TextRange ourRange = ourOwner.getTextRange();
      if (ourRange == null) return false;
      List<ParameterInfoController> allControllers = getAllControllers(myEditor);
      for (ParameterInfoController controller : allControllers) {
        if (controller != ParameterInfoController.this) {
          PsiElement parameterOwner = controller.myComponent.getParameterOwner();
          if (parameterOwner != null && parameterOwner.isValid()) {
            TextRange range = parameterOwner.getTextRange();
            if (range != null && range.contains(myOffset) && ourRange.contains(range)) return false;
          }
        }
      }
      return true;
    }
  }

  private static class MyBestLocationPointProvider  {
    private final Editor myEditor;
    private int previousOffset = -1;
    private Point previousBestPoint;
    private Short previousBestPosition;

    public MyBestLocationPointProvider(final Editor editor) {
      myEditor = editor;
    }

    @NotNull
    public Pair<Point, Short> getBestPointPosition(LightweightHint hint,
                                                   final PsiElement list,
                                                   int offset,
                                                   final boolean awtTooltip,
                                                   short preferredPosition) {
      if (list != null) {
        TextRange range = list.getTextRange();
        if (!range.contains(offset)) {
          offset = range.getStartOffset() + 1;
        }
      }
      if (previousOffset == offset) return Pair.create(previousBestPoint, previousBestPosition);

      final boolean isMultiline = list != null && StringUtil.containsAnyChar(list.getText(), "\n\r");
      final LogicalPosition pos = myEditor.offsetToLogicalPosition(offset).leanForward(true);
      Pair<Point, Short> position;

      if (!isMultiline) {
        position = chooseBestHintPosition(myEditor.getProject(), myEditor, pos, hint, awtTooltip, preferredPosition);
      }
      else {
        Point p = HintManagerImpl.getHintPosition(hint, myEditor, pos, HintManager.ABOVE);
        position = new Pair<>(p, HintManager.ABOVE);
      }
      previousBestPoint = position.getFirst();
      previousBestPosition = position.getSecond();
      previousOffset = offset;
      return position;
    }
  }
}
