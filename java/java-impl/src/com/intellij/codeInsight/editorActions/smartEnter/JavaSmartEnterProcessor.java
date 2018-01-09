/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
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
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.editorActions.smartEnter.JavaSmartEnterProcessor");

  private static final Fixer[] ourFixers;
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

  static {
    final List<Fixer> fixers = new ArrayList<>();
    fixers.add(new LiteralFixer());
    fixers.add(new MethodCallFixer());
    fixers.add(new IfConditionFixer());
    fixers.add(new ForStatementFixer());
    fixers.add(new WhileConditionFixer());
    fixers.add(new CatchDeclarationFixer());
    fixers.add(new SwitchExpressionFixer());
    fixers.add(new SwitchLabelColonFixer());
    fixers.add(new DoWhileConditionFixer());
    fixers.add(new BlockBraceFixer());
    fixers.add(new MissingIfBranchesFixer());
    fixers.add(new MissingWhileBodyFixer());
    fixers.add(new MissingTryBodyFixer());
    fixers.add(new MissingSwitchBodyFixer());
    fixers.add(new MissingCatchBodyFixer());
    fixers.add(new MissingSynchronizedBodyFixer());
    fixers.add(new MissingForBodyFixer());
    fixers.add(new MissingForeachBodyFixer());
    fixers.add(new ParameterListFixer());
    fixers.add(new MissingMethodBodyFixer());
    fixers.add(new MissingClassBodyFixer());
    fixers.add(new MissingReturnExpressionFixer());
    fixers.add(new MissingThrowExpressionFixer());
    fixers.add(new ParenthesizedFixer());
    fixers.add(new SemicolonFixer());
    fixers.add(new MissingArrayInitializerBraceFixer());
    fixers.add(new MissingArrayConstructorBracketFixer());
    fixers.add(new EnumFieldFixer());
    ourFixers = fixers.toArray(new Fixer[fixers.size()]);
  }

  private int myFirstErrorOffset = Integer.MAX_VALUE;
  private boolean mySkipEnter;
  private static final int MAX_ATTEMPTS = 20;
  private static final Key<Long> SMART_ENTER_TIMESTAMP = Key.create("smartEnterOriginalTimestamp");

  public static class TooManyAttemptsException extends Exception {}
  
  private final JavadocFixer myJavadocFixer = new JavadocFixer();

  @Override
  public boolean process(@NotNull final Project project, @NotNull final Editor editor, @NotNull final PsiFile psiFile) {
    FeatureUsageTracker.getInstance().triggerFeatureUsed("codeassists.complete.statement");

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
          fixer.apply(editor, this, psiElement);
          if (LookupManager.getInstance(file.getProject()).getActiveLookup() != null) {
            return;
          }
          if (isUncommited(file.getProject()) || !psiElement.isValid()) {
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

  private static void collectAllElements(PsiElement atCaret, List<PsiElement> res, boolean recurse) {
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
    if (atCaret instanceof PsiJavaToken && "}".equals(atCaret.getText())) {
      atCaret = atCaret.getParent();
      if (!(atCaret instanceof PsiAnonymousClass ||
            atCaret instanceof PsiArrayInitializerExpression ||
            psiElement(PsiCodeBlock.class).withParent(PsiLambdaExpression.class).accepts(atCaret))) {
        return null;
      }
    }

    PsiElement statementAtCaret = PsiTreeUtil.getParentOfType(atCaret,
                                                              PsiStatement.class,
                                                              PsiCodeBlock.class,
                                                              PsiMember.class,
                                                              PsiAnnotation.class,
                                                              PsiComment.class,
                                                              PsiImportStatementBase.class,
                                                              PsiPackageStatement.class
    );

    if (statementAtCaret instanceof PsiBlockStatement) return null;

    if (statementAtCaret != null && statementAtCaret.getParent() instanceof PsiForStatement) {
      if (!PsiTreeUtil.hasErrorElements(statementAtCaret)) {
        statementAtCaret = statementAtCaret.getParent();
      }
    }

    return statementAtCaret instanceof PsiStatement ||
           statementAtCaret instanceof PsiMember ||
           statementAtCaret instanceof PsiAnnotation ||
           statementAtCaret instanceof PsiImportStatementBase ||
           statementAtCaret instanceof PsiPackageStatement
           ? statementAtCaret
           : null;
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
      final CommonCodeStyleSettings settings = CodeStyleSettingsManager.getSettings(file.getProject()).getCommonSettings(JavaLanguage.INSTANCE);
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
    getEnterHandler().execute(editor, ((EditorEx) editor).getDataContext());
  }

  protected static EditorActionHandler getEnterHandler() {
    return EditorActionManager.getInstance().getActionHandler(IdeActions.ACTION_EDITOR_START_NEW_LINE);
  }

  protected static boolean isModified(@NotNull final Editor editor) {
    final Long timestamp = editor.getUserData(SMART_ENTER_TIMESTAMP);
    return editor.getDocument().getModificationStamp() != timestamp.longValue();
  }

}
