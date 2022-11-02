// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.EventId1;
import com.intellij.internal.statistic.eventLog.events.StringEventField;
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.patterns.PsiJavaPatterns.psiElement;

/**
 * @author spleaner
 */
public class JavaSmartEnterProcessor extends SmartEnterProcessor {
  private static final Logger LOG = Logger.getInstance(JavaSmartEnterProcessor.class);

  private static final List<Fixer> ourFixers =
    List.of(new LiteralFixer(),
            new MethodCallFixer(),
            new IfConditionFixer(),
            new ForStatementFixer(),
            new TernaryColonFixer(),
            new WhileConditionFixer(),
            new CatchDeclarationFixer(),
            new SwitchExpressionFixer(),
            new SwitchLabelColonFixer(),
            new DoWhileConditionFixer(),
            new BlockBraceFixer(),
            new MissingIfBranchesFixer(),
            new MissingTryBodyFixer(),
            new MissingSwitchBodyFixer(),
            new MissingLambdaBodyFixer(),
            new MissingCatchBodyFixer(),
            new MissingSynchronizedBodyFixer(),
            new MissingLoopBodyFixer(),
            new ParameterListFixer(),
            new MissingCommaFixer(),
            new MissingMethodBodyFixer(),
            new MissingClassBodyFixer(),
            new MissingReturnExpressionFixer(),
            new MissingThrowExpressionFixer(),
            new ParenthesizedFixer(),
            new SemicolonFixer(),
            new MissingArrayInitializerBraceFixer(),
            new MissingArrayConstructorBracketFixer(),
            new EnumFieldFixer());
  private static final EnterProcessor[] ourEnterProcessors = {
    new CommentBreakerEnterProcessor(),
    new AfterSemicolonEnterProcessor(),
    new LeaveCodeBlockEnterProcessor(),
    new PlainEnterProcessor()
  };
  private static final EnterProcessor[] ourAfterCompletionEnterProcessors = {
    new AfterSemicolonEnterProcessor(),
    new EnterProcessor() {
      @Override
      public boolean doEnter(Editor editor, PsiElement psiElement, boolean isModified) {
        return PlainEnterProcessor.expandCodeBlock(editor, psiElement);
      }
    }
  };

  private int myFirstErrorOffset = Integer.MAX_VALUE;
  private boolean mySkipEnter;
  private static final int MAX_ATTEMPTS = 20;
  private static final Key<Long> SMART_ENTER_TIMESTAMP = Key.create("smartEnterOriginalTimestamp");

  private static class TooManyAttemptsException extends Exception {}

  private final JavadocFixer myJavadocFixer = new JavadocFixer();

  @Override
  public boolean process(@NotNull final Project project, @NotNull final Editor editor, @NotNull final PsiFile psiFile) {
    return invokeProcessor(editor, psiFile, false);
  }

  @Override
  public boolean processAfterCompletion(@NotNull Editor editor, @NotNull PsiFile psiFile) {
    return invokeProcessor(editor, psiFile, true);
  }

  private boolean invokeProcessor(Editor editor, PsiFile psiFile, boolean afterCompletion) {
    final Document document = editor.getDocument();
    final CharSequence textForRollback = document.getImmutableCharSequence();
    try {
      editor.putUserData(SMART_ENTER_TIMESTAMP, editor.getDocument().getModificationStamp());
      myFirstErrorOffset = Integer.MAX_VALUE;
      mySkipEnter = false;
      process(editor, psiFile, 0, afterCompletion);
    }
    catch (TooManyAttemptsException e) {
      document.replaceString(0, document.getTextLength(), textForRollback);
    } finally {
      editor.putUserData(SMART_ENTER_TIMESTAMP, null);
    }
    return true;
  }

  private void process(@NotNull final Editor editor, @NotNull final PsiFile file, final int attempt, boolean afterCompletion) throws TooManyAttemptsException {
    if (attempt > MAX_ATTEMPTS) throw new TooManyAttemptsException();

    try {
      commit(editor);
      if (myFirstErrorOffset != Integer.MAX_VALUE) {
        editor.getCaretModel().moveToOffset(myFirstErrorOffset);
      }

      myFirstErrorOffset = Integer.MAX_VALUE;

      PsiElement atCaret = getStatementAtCaret(editor, file);
      if (atCaret == null) {
        if (myJavadocFixer.process(editor, file)) {
          return;
        }
        if (!new CommentBreakerEnterProcessor().doEnter(editor, file, false)) {
          plainEnter(editor);
        }
        return;
      }

      List<PsiElement> queue = new ArrayList<>();
      collectAllElements(atCaret, queue, true);
      queue.add(atCaret);

      for (PsiElement psiElement : queue) {
        for (Fixer fixer : ourFixers) {
          Document document = editor.getDocument();
          int offset = myFirstErrorOffset;
          long stamp = document.getModificationStamp();
          fixer.apply(editor, this, psiElement);
          Project project = file.getProject();
          if (document.getModificationStamp() != stamp || offset != myFirstErrorOffset) {
            FixerUsageCollector.log(project, fixer);
          }
          if (LookupManager.getInstance(project).getActiveLookup() != null) {
            return;
          }
          if (isUncommited(project) || !psiElement.isValid()) {
            moveCaretInsideBracesIfAny(editor, file);
            process(editor, file, attempt + 1, afterCompletion);
            return;
          }
        }
      }

      doEnter(atCaret, editor, afterCompletion);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }


  @Override
  protected void reformat(PsiElement atCaret) throws IncorrectOperationException {
    if (atCaret == null) {
      return;
    }
    PsiElement parent = atCaret.getParent();
    if (parent instanceof PsiForStatement) {
      atCaret = parent;
    }

    if (parent instanceof PsiIfStatement && atCaret == ((PsiIfStatement)parent).getElseBranch()) {
      PsiFile file = atCaret.getContainingFile();
      Document document = file.getViewProvider().getDocument();
      if (document != null) {
        TextRange elseIfRange = atCaret.getTextRange();
        int lineStart = document.getLineStartOffset(document.getLineNumber(elseIfRange.getStartOffset()));
        CodeStyleManager.getInstance(atCaret.getProject()).reformatText(file, lineStart, elseIfRange.getEndOffset());
        return;
      }
    }

    super.reformat(atCaret);
  }


  private void doEnter(PsiElement atCaret, Editor editor, boolean afterCompletion) throws IncorrectOperationException {
    final PsiFile psiFile = atCaret.getContainingFile();

    if (myFirstErrorOffset != Integer.MAX_VALUE) {
      editor.getCaretModel().moveToOffset(myFirstErrorOffset);
      reformat(atCaret);
      return;
    }

    final RangeMarker rangeMarker = createRangeMarker(atCaret);
    reformat(atCaret);
    commit(editor);

    if (!mySkipEnter) {
      atCaret = CodeInsightUtil.findElementInRange(psiFile, rangeMarker.getStartOffset(), rangeMarker.getEndOffset(), atCaret.getClass());
      for (EnterProcessor processor : afterCompletion ? ourAfterCompletionEnterProcessors : ourEnterProcessors) {
        if (atCaret == null) {
          // Can't restore element at caret after enter processor execution!
          break;
        }

        if (processor.doEnter(editor, atCaret, isModified(editor))) {
          rangeMarker.dispose();
          return;
        }
      }

      if (!isModified(editor) && !afterCompletion) {
        plainEnter(editor);
      } else {
        if (myFirstErrorOffset == Integer.MAX_VALUE) {
          editor.getCaretModel().moveToOffset(rangeMarker.getEndOffset());
        } else {
          editor.getCaretModel().moveToOffset(myFirstErrorOffset);
        }
      }
    }
    rangeMarker.dispose();
  }

  private static void collectAllElements(PsiElement atCaret, List<? super PsiElement> res, boolean recurse) {
    res.add(0, atCaret);
    if (doNotStepInto(atCaret)) {
      if (!recurse) return;
      recurse = false;
    }

    final PsiElement[] children = atCaret.getChildren();
    for (PsiElement child : children) {
      if (atCaret instanceof PsiStatement && child instanceof PsiStatement &&
          !(atCaret instanceof PsiForStatement && child == ((PsiForStatement)atCaret).getInitialization())) continue;
      collectAllElements(child, res, recurse);
    }
  }

  private static boolean doNotStepInto(PsiElement element) {
    return element instanceof PsiClass || element instanceof PsiCodeBlock || element instanceof PsiStatement || element instanceof PsiMethod;
  }

  @Override
  @Nullable
  protected PsiElement getStatementAtCaret(Editor editor, PsiFile psiFile) {
    PsiElement atCaret = super.getStatementAtCaret(editor, psiFile);

    if (atCaret instanceof PsiWhiteSpace) return null;
    if (PsiUtil.isJavaToken(atCaret, JavaTokenType.RBRACE)) {
      atCaret = atCaret.getParent();
      boolean expressionEndingWithBrace = atCaret instanceof PsiAnonymousClass ||
                                          atCaret instanceof PsiArrayInitializerExpression ||
                                          atCaret instanceof PsiCodeBlock && (
                                            atCaret.getParent() instanceof PsiLambdaExpression ||
                                            atCaret.getParent() instanceof PsiSwitchExpression
                                          );
      if (!expressionEndingWithBrace) return null;
    }

    for (PsiElement each : SyntaxTraverser.psiApi().parents(atCaret).skip(1)) {
      if (each instanceof PsiMember ||
          each instanceof PsiImportStatementBase ||
          each instanceof PsiPackageStatement ||
          each instanceof PsiAnnotation && PsiTreeUtil.hasErrorElements(each)) {
        return each;
      }
      if (each instanceof PsiCodeBlock || each instanceof PsiComment) {
        return null;
      }
      if (each instanceof PsiStatement) {
        return each.getParent() instanceof PsiForStatement && !PsiTreeUtil.hasErrorElements(each) ? each.getParent() : each;
      }
      if (each instanceof PsiConditionalExpression && PsiUtilCore.hasErrorElementChild(each)) {
        return each;
      }
    }

    return null;
  }

  protected void moveCaretInsideBracesIfAny(@NotNull final Editor editor, @NotNull final PsiFile file) throws IncorrectOperationException {
    int caretOffset = editor.getCaretModel().getOffset();
    final CharSequence chars = editor.getDocument().getCharsSequence();

    if (CharArrayUtil.regionMatches(chars, caretOffset, "{}")) {
      caretOffset+=2;
    }
    else if (CharArrayUtil.regionMatches(chars, caretOffset, "{\n}")) {
      caretOffset+=3;
    }

    caretOffset = CharArrayUtil.shiftBackward(chars, caretOffset - 1, " \t") + 1;

    if (CharArrayUtil.regionMatches(chars, caretOffset - "{}".length(), "{}") ||
        CharArrayUtil.regionMatches(chars, caretOffset - "{\n}".length(), "{\n}")) {
      commit(editor);
      final CommonCodeStyleSettings settings = CodeStyle.getSettings(file).getCommonSettings(JavaLanguage.INSTANCE);
      final boolean old = settings.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE;
      settings.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = false;
      PsiElement leaf = file.findElementAt(caretOffset - 1);
      PsiElement elt = PsiTreeUtil.getParentOfType(leaf, PsiCodeBlock.class);
      if (elt == null && leaf != null && leaf.getParent() instanceof PsiClass) {
        elt = leaf.getParent();
      }
      reformat(elt);
      settings.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = old;
      editor.getCaretModel().moveToOffset(caretOffset - 1);

      reformatBlockParentIfNeeded(editor, file);
    }
  }

  private void reformatBlockParentIfNeeded(@NotNull Editor editor, @NotNull PsiFile file) {
    commit(editor);
    PsiCodeBlock block = PsiTreeUtil.findElementOfClassAtOffset(file, editor.getCaretModel().getOffset(), PsiCodeBlock.class, false);
    if (block != null && psiElement().withParents(PsiBlockStatement.class, PsiForStatement.class).accepts(block)) {
      reformat(block.getParent().getParent());
    }
  }

  public void registerUnresolvedError(int offset) {
    if (myFirstErrorOffset > offset) {
      myFirstErrorOffset = offset;
    }
  }

  public void setSkipEnter(boolean skipEnter) {
    mySkipEnter = skipEnter;
  }

  protected static void plainEnter(@NotNull final Editor editor) {
    getEnterHandler().execute(editor, editor.getCaretModel().getCurrentCaret(), EditorUtil.getEditorDataContext(editor));
  }

  protected static EditorActionHandler getEnterHandler() {
    return EditorActionManager.getInstance().getActionHandler(IdeActions.ACTION_EDITOR_START_NEW_LINE);
  }

  protected static boolean isModified(@NotNull final Editor editor) {
    final Long timestamp = editor.getUserData(SMART_ENTER_TIMESTAMP);
    return editor.getDocument().getModificationStamp() != timestamp.longValue();
  }

  private static final class FixerUsageCollector extends CounterUsagesCollector {
    private static final EventLogGroup GROUP = new EventLogGroup("java.smart.enter.fixer", 3);
    private static final EventId1<String> USED = GROUP.registerEvent("fixer_used", new StringEventField.ValidatedByAllowedValues(
      "fixer_used",
      ContainerUtil.map(ourFixers, f -> f.getClass().getSimpleName())));

    @Override
    public EventLogGroup getGroup() {
      return GROUP;
    }
    
    static void log(Project project, Fixer fixer) {
      USED.log(project, fixer.getClass().getSimpleName());
    }
  }
}
