// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hint;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.lang.ASTNode;
import com.intellij.lang.parameterInfo.*;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.Alarm;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;

import static com.intellij.codeInsight.hint.ParameterInfoTaskRunnerUtil.runTask;

public abstract class ParameterInfoControllerBase extends UserDataHolderBase implements Disposable {
  protected static final String WHITESPACE = " \t";

  protected final Project myProject;
  protected final @NotNull Editor myEditor;

  protected final RangeMarker myLbraceMarker;
  private final CaretListener myEditorCaretListener;

  protected final @NotNull ParameterInfoControllerData myParameterInfoControllerData;

  protected final Alarm myAlarm = new Alarm();
  protected static final int DELAY = 200;

  protected boolean mySingleParameterInfo;
  protected boolean myDisposed;
  /**
   * Keeps Vector of ParameterInfoControllerBase's in Editor
   */
  private static final Key<List<ParameterInfoControllerBase>> ALL_CONTROLLERS_KEY =
    Key.create("ParameterInfoControllerBase.ALL_CONTROLLERS_KEY");

  public static ParameterInfoControllerBase findControllerAtOffset(Editor editor, int offset) {
    for (ParameterInfoControllerBase controller : new ArrayList<>(getAllControllers(editor))) {
      int lbraceOffset = controller.myLbraceMarker.getStartOffset();
      if (lbraceOffset == offset) {
        if (!controller.canBeDisposed()) {
          return controller;
        }
        Disposer.dispose(controller);
      }
    }

    return null;
  }

  static List<ParameterInfoControllerBase> getAllControllers(@NotNull Editor editor) {
    List<ParameterInfoControllerBase> array = editor.getUserData(ALL_CONTROLLERS_KEY);
    if (array == null) {
      array = ((UserDataHolderEx)editor).putUserDataIfAbsent(ALL_CONTROLLERS_KEY, new CopyOnWriteArrayList<>());
    }
    return array;
  }

  public static boolean existsForEditor(@NotNull Editor editor) {
    return !getAllControllers(editor).isEmpty();
  }

  public static boolean existsWithVisibleHintForEditor(@NotNull Editor editor, boolean anyHintType) {
    return getAllControllers(editor).stream().anyMatch(c -> c.isHintShown(anyHintType));
  }

  public abstract boolean isHintShown(boolean anyType);


  public ParameterInfoControllerBase(@NotNull Project project,
                                     @NotNull Editor editor,
                                     int lbraceOffset,
                                     Object[] descriptors,
                                     Object highlighted,
                                     PsiElement parameterOwner,
                                     @NotNull ParameterInfoHandler handler,
                                     boolean showHint) {
    ThreadingAssertions.assertEventDispatchThread(); // DEXP-575205

    myProject = project;
    myEditor = editor;

    //noinspection unchecked
    myParameterInfoControllerData = createParameterInfoControllerData(handler);
    myParameterInfoControllerData.setDescriptors(descriptors);
    myParameterInfoControllerData.setHighlighted(highlighted);
    myParameterInfoControllerData.setParameterOwner(parameterOwner);

    myLbraceMarker = editor.getDocument().createRangeMarker(lbraceOffset, lbraceOffset);

    mySingleParameterInfo = !showHint;

    myEditorCaretListener = new CaretListener() {
      @Override
      public void caretPositionChanged(@NotNull CaretEvent e) {
        if (!UndoManager.getInstance(myProject).isUndoOrRedoInProgress()) {
          syncUpdateOnCaretMove();
          rescheduleUpdate();
        }
      }
    };
  }

  // TODO [V.Petrenko] need to make a proper logic of creation an instance of this class
  //  without such inconvenient methods like registerSelf() and setupListeners()
  //  considering possible exceptions in constructors of inheritors
  protected final void registerSelf() {
    List<ParameterInfoControllerBase> allControllers = getAllControllers(myEditor);
    allControllers.add(this);
  }

  protected void setupListeners() {
    myEditor.getCaretModel().addCaretListener(myEditorCaretListener);

    myEditor.getDocument().addDocumentListener(new DocumentListener() {
      @Override
      public void documentChanged(@NotNull DocumentEvent e) {
        rescheduleUpdate();
      }
    }, this);

    MessageBusConnection connection = myProject.getMessageBus().connect(this);
    connection.subscribe(ExternalParameterInfoChangesProvider.TOPIC, (e, offset) -> {
      if (e != null && (e != myEditor || myLbraceMarker.getStartOffset() != offset)) return;
      updateWhenAllCommitted();
    });

    EditorUtil.disposeWithEditor(myEditor, this);

    myProject.getMessageBus().connect(this).subscribe(DumbService.DUMB_MODE, new DumbService.DumbModeListener() {
      @Override
      public void enteredDumbMode() {
        updateComponent();
      }

      @Override
      public void exitDumbMode() {
        updateComponent();
      }
    });
  }

  public void setDescriptors(Object[] descriptors) {
    myParameterInfoControllerData.setDescriptors(descriptors);
  }

  protected void syncUpdateOnCaretMove() {
    myParameterInfoControllerData.getHandler().syncUpdateOnCaretMove(new MyLazyUpdateParameterInfoContext());
  }

  protected @NotNull ParameterInfoControllerData createParameterInfoControllerData(@NotNull ParameterInfoHandler<PsiElement, Object> handler) {
    return new ParameterInfoControllerData(handler);
  }

  protected abstract boolean canBeDisposed();

  @Override
  public void dispose() {
    if (myDisposed) return;
    myDisposed = true;
    hideHint();
    myParameterInfoControllerData.getHandler().dispose(new MyDeleteParameterInfoContext());
    List<ParameterInfoControllerBase> allControllers = getAllControllers(myEditor);
    allControllers.remove(this);
    myEditor.getCaretModel().removeCaretListener(myEditorCaretListener);
  }

  public abstract void showHint(boolean requestFocus, boolean singleParameterInfo);

  static boolean hasPrevOrNextParameter(Editor editor, int lbraceOffset, boolean isNext) {
    ParameterInfoControllerBase controller = findControllerAtOffset(editor, lbraceOffset);
    return controller != null && controller.getPrevOrNextParameterOffset(isNext) != -1;
  }

  protected int getCurrentOffset() {
    int caretOffset = myEditor.getCaretModel().getOffset();
    CharSequence chars = myEditor.getDocument().getCharsSequence();
    return myParameterInfoControllerData.getHandler().isWhitespaceSensitive() ? caretOffset :
           CharArrayUtil.shiftBackward(chars, caretOffset - 1, WHITESPACE) + 1;
  }

  protected void executeFindElementForUpdatingParameterInfo(UpdateParameterInfoContext context,
                                                            @NotNull Consumer<? super PsiElement> elementForUpdatingConsumer) {
    runTask(myProject,
            ReadAction
              .nonBlocking(() -> {
                return myParameterInfoControllerData.getHandler().findElementForUpdatingParameterInfo(context);
              }).withDocumentsCommitted(myProject)
              .expireWhen(() -> getCurrentOffset() != context.getOffset())
              .coalesceBy(this)
              .expireWith(this),
            elementForUpdatingConsumer,
            null,
            myEditor);
  }

  protected void rescheduleUpdate() {
    myAlarm.cancelAllRequests();
    myAlarm.addRequest(() -> updateWhenAllCommitted(), DELAY, ModalityState.stateForComponent(myEditor.getComponent()));
  }

  protected void updateWhenAllCommitted() {
    if (!myDisposed && !myProject.isDisposed()) {
      PsiDocumentManager.getInstance(myProject).performLaterWhenAllCommitted(this::updateComponent);
    }
  }

  public abstract void updateComponent();

  protected abstract void moveToParameterAtOffset(int offset);

  protected int getPrevOrNextParameterOffset(boolean isNext) {
    if (!(myParameterInfoControllerData.getHandler() instanceof ParameterInfoHandlerWithTabActionSupport handler)) return -1;

    IElementType delimiter = handler.getActualParameterDelimiterType();
    boolean noDelimiter = delimiter == TokenType.WHITE_SPACE;
    int caretOffset = myEditor.getCaretModel().getOffset();
    CharSequence text = myEditor.getDocument().getImmutableCharSequence();
    int offset = noDelimiter ? caretOffset : CharArrayUtil.shiftBackward(text, caretOffset - 1, WHITESPACE) + 1;
    int lbraceOffset = myLbraceMarker.getStartOffset();
    PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(myEditor.getDocument());
    PsiElement argList = lbraceOffset < offset ? findArgumentList(file, offset, lbraceOffset) : null;
    if (argList == null) return -1;

    @SuppressWarnings("unchecked") PsiElement[] parameters = handler.getActualParameters(argList);
    int currentParameterIndex = getParameterIndex(parameters, delimiter, offset);
    if (CodeInsightSettings.getInstance().SHOW_PARAMETER_NAME_HINTS_ON_COMPLETION) {
      if (currentParameterIndex < 0 || currentParameterIndex >= parameters.length && parameters.length > 0) return -1;
      if (offset >= argList.getTextRange().getEndOffset()) currentParameterIndex = isNext ? -1 : parameters.length;
      int prevOrNextParameterIndex = currentParameterIndex + (isNext ? 1 : -1);
      if (prevOrNextParameterIndex < 0 || prevOrNextParameterIndex >= parameters.length) {
        PsiElement parameterOwner = myParameterInfoControllerData.getParameterOwner();
        return parameterOwner != null && parameterOwner.isValid() ? parameterOwner.getTextRange().getEndOffset() : -1;
      }
      else {
        return getParameterNavigationOffset(parameters[prevOrNextParameterIndex], text);
      }
    }
    else {
      int prevOrNextParameterIndex = isNext && currentParameterIndex < parameters.length - 1 ? currentParameterIndex + 1 :
                                     !isNext && currentParameterIndex > 0 ? currentParameterIndex - 1 : -1;
      return prevOrNextParameterIndex != -1 ? parameters[prevOrNextParameterIndex].getTextRange().getStartOffset() : -1;
    }
  }

  private static int getParameterIndex(@NotNull PsiElement[] parameters, @NotNull IElementType delimiter, int offset) {
    for (int i = 0; i < parameters.length; i++) {
      PsiElement parameter = parameters[i];
      TextRange textRange = parameter.getTextRange();
      int startOffset = textRange.getStartOffset();
      if (offset < startOffset) {
        if (i == 0) return 0;
        PsiElement elementInBetween = parameters[i - 1];
        int currOffset = elementInBetween.getTextRange().getEndOffset();
        while ((elementInBetween = PsiTreeUtil.nextLeaf(elementInBetween)) != null) {
          if (currOffset >= startOffset) break;
          ASTNode node = elementInBetween.getNode();
          if (node != null && node.getElementType() == delimiter) {
            return offset <= currOffset ? i - 1 : i;
          }
          currOffset += elementInBetween.getTextLength();
        }
        return i;
      }
      else if (offset <= textRange.getEndOffset()) {
        return i;
      }
    }
    return Math.max(0, parameters.length - 1);
  }

  protected static int getParameterNavigationOffset(@NotNull PsiElement parameter, @NotNull CharSequence text) {
    int rangeStart = parameter.getTextRange().getStartOffset();
    int rangeEnd = parameter.getTextRange().getEndOffset();
    int offset = CharArrayUtil.shiftBackward(text, rangeEnd - 1, WHITESPACE) + 1;
    return offset > rangeStart ? offset : CharArrayUtil.shiftForward(text, rangeEnd, WHITESPACE);
  }

  public static @Nullable <E extends PsiElement> E findArgumentList(PsiFile file, int offset, int lbraceOffset) {
    if (file == null) return null;
    ParameterInfoHandler[] handlers = ShowParameterInfoHandler.getHandlers(file.getProject(), PsiUtilCore.getLanguageAtOffset(file, offset), file.getViewProvider().getBaseLanguage());

    for (ParameterInfoHandler handler : handlers) {
      if (handler instanceof ParameterInfoHandlerWithTabActionSupport parameterInfoHandler2) {

        // please don't remove typecast in the following line; it's required to compile the code under old JDK 6 versions
        final E e = ParameterInfoUtils.findArgumentList(file, offset, lbraceOffset, parameterInfoHandler2);
        if (e != null) return e;
      }
    }

    return null;
  }

  public Object[] getObjects() {
    return myParameterInfoControllerData.getDescriptors();
  }

  public Object getHighlighted() {
    return myParameterInfoControllerData.getHighlighted();
  }

  public abstract void setPreservedOnHintHidden(boolean value);

  public abstract boolean isPreservedOnHintHidden();

  @TestOnly
  public static void waitForDelayedActions(@NotNull Editor editor, long timeout, @NotNull TimeUnit unit) throws TimeoutException {
    long deadline = System.currentTimeMillis() + unit.toMillis(timeout);
    while (System.currentTimeMillis() < deadline) {
      List<ParameterInfoControllerBase> controllers = getAllControllers(editor);
      boolean hasPendingRequests = false;
      for (ParameterInfoControllerBase controller : controllers) {
        if (!controller.myAlarm.isEmpty()) {
          hasPendingRequests = true;
          break;
        }
      }
      if (hasPendingRequests) {
        LockSupport.parkNanos(10_000_000);
        UIUtil.dispatchAllInvocationEvents();
      }
      else {
        return;
      }
    }
    throw new TimeoutException();
  }

  public static boolean areParameterTemplatesEnabledOnCompletion() {
    return Registry.is("java.completion.argument.live.template") &&
           !CodeInsightSettings.getInstance().SHOW_PARAMETER_NAME_HINTS_ON_COMPLETION;
  }

  public static @NotNull ParameterInfoControllerBase createParameterInfoController(@NotNull Project project,
                                                                                   @NotNull Editor editor,
                                                                                   int lbraceOffset,
                                                                                   Object[] descriptors,
                                                                                   Object highlighted,
                                                                                   PsiElement parameterOwner,
                                                                                   @NotNull ParameterInfoHandler handler,
                                                                                   boolean showHint,
                                                                                   boolean requestFocus) {
    for (ParameterInfoControllerProvider provider : ParameterInfoControllerProvider.EP_NAME.getExtensions()) {
      ParameterInfoControllerBase controller = provider.create(project, editor, lbraceOffset,
                                                               descriptors, highlighted, parameterOwner,
                                                               handler, showHint, requestFocus);
      if (controller != null) return controller;
    }

    return new ParameterInfoController(project, editor, lbraceOffset, descriptors, highlighted,
                                       parameterOwner, handler, showHint, requestFocus);
  }


  protected class UpdateParameterInfoContextBase implements UpdateParameterInfoContext {
    protected final int myOffset;
    protected final @Nullable PsiFile myFile;
    private final boolean[] enabled;

    public UpdateParameterInfoContextBase(int offset, @Nullable PsiFile file) {
      myOffset = offset;
      myFile = file;

      enabled = new boolean[myParameterInfoControllerData.getDescriptors().length];
      for (int i = 0; i < enabled.length; i++) {
        enabled[i] = myParameterInfoControllerData.isDescriptorEnabled(i);
      }
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
    public @Nullable PsiFile getFile() {
      return myFile;
    }

    @Override
    public @NotNull Editor getEditor() {
      return myEditor;
    }

    @Override
    public void removeHint() {
      ApplicationManager.getApplication().invokeLater(() -> {
        if (!isHintShown(true)) return;

        hideHint();
        if (!isPreservedOnHintHidden()) Disposer.dispose(ParameterInfoControllerBase.this);
      });
    }

    @Override
    public void setParameterOwner(final PsiElement o) {
      myParameterInfoControllerData.setParameterOwner(o);
    }

    @Override
    public PsiElement getParameterOwner() {
      return myParameterInfoControllerData.getParameterOwner();
    }

    @Override
    public void setHighlightedParameter(final Object method) {
      myParameterInfoControllerData.setHighlighted(method);
    }

    @Override
    public Object getHighlightedParameter() {
      return myParameterInfoControllerData.getHighlighted();
    }

    @Override
    public void setCurrentParameter(final int index) {
      myParameterInfoControllerData.setCurrentParameterIndex(index);
    }

    @Override
    public boolean isUIComponentEnabled(int index) {
      return enabled[index];
    }

    @Override
    public void setUIComponentEnabled(int index, boolean enabled) {
      this.enabled[index] = enabled;
    }

    @Override
    public Object[] getObjectsToView() {
      return myParameterInfoControllerData.getDescriptors();
    }

    @Override
    public boolean isPreservedOnHintHidden() {
      return ParameterInfoControllerBase.this.isPreservedOnHintHidden();
    }

    @Override
    public void setPreservedOnHintHidden(boolean value) {
      ParameterInfoControllerBase.this.setPreservedOnHintHidden(value);
    }

    @Override
    public boolean isInnermostContext() {
      PsiElement ourOwner = getParameterOwner();
      if (ourOwner == null || !ourOwner.isValid()) return false;
      TextRange ourRange = ourOwner.getTextRange();
      if (ourRange == null) return false;
      List<ParameterInfoControllerBase> allControllers = getAllControllers(myEditor);
      for (ParameterInfoControllerBase controller : allControllers) {
        if (controller != ParameterInfoControllerBase.this) {
          PsiElement parameterOwner = controller.myParameterInfoControllerData.getParameterOwner();
          if (parameterOwner != null && parameterOwner.isValid()) {
            TextRange range = parameterOwner.getTextRange();
            if (range != null && range.contains(myOffset) && ourRange.contains(range)) return false;
          }
        }
      }
      return true;
    }

    @Override
    public boolean isSingleParameterInfo() {
      return mySingleParameterInfo;
    }

    @Override
    public UserDataHolderEx getCustomContext() {
      return ParameterInfoControllerBase.this;
    }

    public void applyUIChanges() {
      ThreadingAssertions.assertEventDispatchThread();

      for (int index = 0, len = getObjects().length; index < len; index++) {
        boolean enabled = isUIComponentEnabled(index);
        if (enabled != myParameterInfoControllerData.isDescriptorEnabled(index)) {
          myParameterInfoControllerData.setDescriptorEnabled(index, enabled);
        }
      }
    }
  }

  private final class MyLazyUpdateParameterInfoContext extends UpdateParameterInfoContextBase {
    private PsiFile myLazyFile;

    private MyLazyUpdateParameterInfoContext() {
      super(myEditor.getCaretModel().getOffset(), null);
    }

    @Override
    public PsiFile getFile() {
      if (myLazyFile == null) {
        myLazyFile = PsiUtilBase.getPsiFileInEditor(myEditor, myProject);
      }
      return myLazyFile;
    }
  }

  protected abstract void hideHint();

  public interface SignatureItemModel {
  }

  public static final class RawSignatureItem implements SignatureItemModel {
    public final String htmlText;

    public RawSignatureItem(String htmlText) {
      this.htmlText = htmlText;
    }
  }

  public static final class SignatureItem implements SignatureItemModel {
    public final String text;
    public final boolean deprecated;
    public final boolean disabled;
    public final List<Integer> startOffsets;
    public final List<Integer> endOffsets;

    public SignatureItem(String text,
                         boolean deprecated,
                         boolean disabled,
                         List<Integer> startOffsets,
                         List<Integer> endOffsets) {
      this.text = text;
      this.deprecated = deprecated;
      this.disabled = disabled;
      this.startOffsets = startOffsets;
      this.endOffsets = endOffsets;
    }
  }

  public static final class Model {
    public final List<SignatureItemModel> signatures = new ArrayList<>();
    public int current = -1;
    public int highlightedSignature = -1;
    public TextRange range;
    public Editor editor;
    public Project project;
  }

  private final class MyDeleteParameterInfoContext implements DeleteParameterInfoContext {
    @Override
    public PsiElement getParameterOwner() {
      return myParameterInfoControllerData.getParameterOwner();
    }

    @Override
    public Editor getEditor() {
      return myEditor;
    }

    @Override
    public UserDataHolderEx getCustomContext() {
      return ParameterInfoControllerBase.this;
    }
  }
}
