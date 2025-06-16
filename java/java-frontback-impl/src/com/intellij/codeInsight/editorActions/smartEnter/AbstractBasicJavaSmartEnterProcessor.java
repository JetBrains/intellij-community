// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.lang.ASTNode;
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
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SyntaxTraverser;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.impl.source.BasicJavaAstTreeUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.psi.impl.source.BasicElementTypes.BASIC_JAVA_COMMENT_BIT_SET;
import static com.intellij.psi.impl.source.BasicJavaElementType.*;

public abstract class AbstractBasicJavaSmartEnterProcessor extends SmartEnterProcessor {
  private static final Logger LOG = Logger.getInstance(AbstractBasicJavaSmartEnterProcessor.class);

  private final List<Fixer> ourFixers;
  private final EnterProcessor[] ourEnterProcessors;
  private final EnterProcessor[] ourAfterCompletionEnterProcessors;

  protected int myFirstErrorOffset = Integer.MAX_VALUE;
  protected boolean mySkipEnter;
  private static final int MAX_ATTEMPTS = 20;
  private static final Key<Long> SMART_ENTER_TIMESTAMP = Key.create("smartEnterOriginalTimestamp");
  private final EnterProcessor myBreakerEnterProcessor;

  protected void insertBraces(@NotNull Editor editor, int offset) {
    Document document = editor.getDocument();
    document.insertString(offset, "{");
    insertCloseBrace(editor, offset + 1);
  }

  protected void insertCloseBrace(@NotNull Editor editor, int offset) {
    Document document = editor.getDocument();
    document.insertString(offset, "}");
  }

  protected void insertBracesWithNewLine(Editor editor, int offset) {
    Document document = editor.getDocument();
    document.insertString(offset, "{\n");
    insertCloseBrace(editor, offset + 2);
  }

  private static class TooManyAttemptsException extends Exception {
  }

  private final JavadocFixer myJavadocFixer;

  protected AbstractBasicJavaSmartEnterProcessor(@NotNull List<Fixer> fixers,
                                                 EnterProcessor @NotNull [] enterProcessors,
                                                 EnterProcessor @NotNull [] afterCompletionEnterProcessors,
                                                 @NotNull JavadocFixer thinJavadocFixer,
                                                 @NotNull EnterProcessor breakerEnterProcessor) {
    myBreakerEnterProcessor = breakerEnterProcessor;
    ourFixers = fixers;
    ourEnterProcessors = enterProcessors;
    ourAfterCompletionEnterProcessors = afterCompletionEnterProcessors;
    myJavadocFixer = thinJavadocFixer;
  }

  @Override
  public boolean process(final @NotNull Project project, final @NotNull Editor editor, final @NotNull PsiFile psiFile) {
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
    }
    catch (Exception e) {
      LOG.error(e);
    }
    finally {
      editor.putUserData(SMART_ENTER_TIMESTAMP, null);
    }
    return true;
  }

  private void process(final @NotNull Editor editor, final @NotNull PsiFile file, final int attempt, boolean afterCompletion)
    throws TooManyAttemptsException {
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
        if (!(myBreakerEnterProcessor).doEnter(editor, file, false)) {
          plainEnter(editor);
        }
        return;
      }

      List<ASTNode> queue = new ArrayList<>();
      ASTNode caretNode = atCaret.getNode();
      collectAllElements(caretNode, queue, true);
      queue.add(caretNode);

      for (ASTNode astNode : queue) {
        for (Fixer fixer : ourFixers) {
          Document document = editor.getDocument();
          int offset = myFirstErrorOffset;
          long stamp = document.getModificationStamp();
          fixer.apply(editor, this, astNode);
          Project project = file.getProject();
          if (document.getModificationStamp() != stamp || offset != myFirstErrorOffset) {
            log(fixer, project);
          }
          if (LookupManager.getInstance(project).getActiveLookup() != null) {
            return;
          }
          PsiElement psi = BasicJavaAstTreeUtil.toPsi(astNode);
          if (isUncommited(project) || !(psi != null && psi.isValid())) {
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

  protected abstract void log(@NotNull Fixer fixer, @NotNull Project project);


  @Override
  public void reformat(PsiElement atCaretElement) throws IncorrectOperationException {
    if (atCaretElement == null) {
      return;
    }
    ASTNode atCaret = atCaretElement.getNode();
    ASTNode parent = atCaret.getTreeParent();
    if (BasicJavaAstTreeUtil.is(parent, BASIC_FOR_STATEMENT)) {
      atCaret = parent;
    }

    if (BasicJavaAstTreeUtil.is(parent, BASIC_IF_STATEMENT) &&
        atCaret == BasicJavaAstTreeUtil.getElseBranch(parent)) {
      PsiFile file = atCaretElement.getContainingFile();
      Document document = file.getViewProvider().getDocument();
      if (document != null) {
        TextRange elseIfRange = atCaret.getTextRange();
        int lineStart = document.getLineStartOffset(document.getLineNumber(elseIfRange.getStartOffset()));
        CodeStyleManager.getInstance(atCaretElement.getProject()).reformatText(file, lineStart, elseIfRange.getEndOffset());
        return;
      }
    }

    super.reformat(atCaretElement);
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
      ASTNode atCaretNode = BasicJavaAstTreeUtil.findElementInRange(psiFile, rangeMarker.getStartOffset(), rangeMarker.getEndOffset(),
                                                                    atCaret.getNode().getElementType());
      for (EnterProcessor processor : afterCompletion ? ourAfterCompletionEnterProcessors : ourEnterProcessors) {
        if (atCaretNode == null) {
          // Can't restore element at caret after enter processor execution!
          break;
        }

        if (processor.doEnter(editor, BasicJavaAstTreeUtil.toPsi(atCaretNode), isModified(editor))) {
          rangeMarker.dispose();
          return;
        }
      }

      if (!isModified(editor) && !afterCompletion) {
        plainEnter(editor);
      }
      else {
        if (myFirstErrorOffset == Integer.MAX_VALUE) {
          editor.getCaretModel().moveToOffset(rangeMarker.getEndOffset());
        }
        else {
          editor.getCaretModel().moveToOffset(myFirstErrorOffset);
        }
      }
    }
    rangeMarker.dispose();
  }

  private static void collectAllElements(ASTNode atCaret, List<? super ASTNode> res, boolean recurse) {
    res.add(0, atCaret);
    if (doNotStepInto(atCaret)) {
      if (!recurse) return;
      recurse = false;
    }

    final List<ASTNode> children = BasicJavaAstTreeUtil.getChildren(atCaret);
    for (ASTNode child : children) {
      if (BasicJavaAstTreeUtil.is(atCaret, STATEMENT_SET) &&
          BasicJavaAstTreeUtil.is(child, STATEMENT_SET) &&
          !(BasicJavaAstTreeUtil.is(atCaret, BASIC_FOR_STATEMENT)
            && child == (BasicJavaAstTreeUtil.getForInitialization(atCaret)))) {
        continue;
      }
      collectAllElements(child, res, recurse);
    }
  }

  private static boolean doNotStepInto(ASTNode element) {
    return BasicJavaAstTreeUtil.is(element, CLASS_SET) ||
           BasicJavaAstTreeUtil.is(element, BASIC_CODE_BLOCK) ||
           BasicJavaAstTreeUtil.is(element, STATEMENT_SET) ||
           BasicJavaAstTreeUtil.is(element, BASIC_METHOD);
  }

  @Override
  protected @Nullable PsiElement getStatementAtCaret(Editor editor, PsiFile psiFile) {
    PsiElement atCaretElement = super.getStatementAtCaret(editor, psiFile);
    if (atCaretElement == null) {
      return null;
    }
    ASTNode atCaret = atCaretElement.getNode();
    if (BasicJavaAstTreeUtil.isWhiteSpace(atCaret)) return null;
    if (BasicJavaAstTreeUtil.is(atCaret, JavaTokenType.RBRACE)) {
      atCaret = atCaret.getTreeParent();
      boolean expressionEndingWithBrace = BasicJavaAstTreeUtil.is(atCaret, BASIC_ANONYMOUS_CLASS) ||
                                          BasicJavaAstTreeUtil.is(atCaret, BASIC_ARRAY_INITIALIZER_EXPRESSION) ||
                                          BasicJavaAstTreeUtil.is(atCaret, BASIC_CODE_BLOCK) && (
                                            BasicJavaAstTreeUtil.is(atCaret.getTreeParent(), BASIC_LAMBDA_EXPRESSION) ||
                                            BasicJavaAstTreeUtil.is(atCaret.getTreeParent(), BASIC_SWITCH_EXPRESSION));
      if (!expressionEndingWithBrace) return null;
    }

    for (ASTNode each : SyntaxTraverser.astApi().parents(atCaret).skip(1)) {
      PsiElement psiElement = BasicJavaAstTreeUtil.toPsi(each);
      if (BasicJavaAstTreeUtil.is(each, MEMBER_SET) ||
          isImportStatementBase(psiElement) ||
          BasicJavaAstTreeUtil.is(each, BASIC_PACKAGE_STATEMENT) ||
          BasicJavaAstTreeUtil.is(each, BASIC_ANNOTATION) &&
          psiElement != null &&
          PsiTreeUtil.hasErrorElements(psiElement)) {
        return each.getPsi();
      }
      if (BasicJavaAstTreeUtil.is(each, BASIC_CODE_BLOCK) ||
          BasicJavaAstTreeUtil.is(each, BASIC_JAVA_COMMENT_BIT_SET)) {
        return null;
      }
      if (BasicJavaAstTreeUtil.is(each, STATEMENT_SET)) {
        return BasicJavaAstTreeUtil.is(each.getTreeParent(), BASIC_FOR_STATEMENT) &&
               !PsiTreeUtil.hasErrorElements(each.getPsi()) ? each.getPsi().getParent() : each.getPsi();
      }
      if (BasicJavaAstTreeUtil.is(each, BASIC_CONDITIONAL_EXPRESSION) &&
          PsiUtilCore.hasErrorElementChild(each.getPsi())) {
        return each.getPsi();
      }
    }

    return null;
  }

  protected abstract boolean isImportStatementBase(PsiElement el);

  protected void moveCaretInsideBracesIfAny(final @NotNull Editor editor, final @NotNull PsiFile file) throws IncorrectOperationException {
    int caretOffset = editor.getCaretModel().getOffset();
    final CharSequence chars = editor.getDocument().getCharsSequence();

    if (CharArrayUtil.regionMatches(chars, caretOffset, "{}")) {
      caretOffset += 2;
    }
    else if (CharArrayUtil.regionMatches(chars, caretOffset, "{\n}")) {
      caretOffset += 3;
    }

    caretOffset = CharArrayUtil.shiftBackward(chars, caretOffset - 1, " \t") + 1;

    if (CharArrayUtil.regionMatches(chars, caretOffset - "{}".length(), "{}") ||
        CharArrayUtil.regionMatches(chars, caretOffset - "{\n}".length(), "{\n}")) {
      commit(editor);
      final CommonCodeStyleSettings settings = CodeStyle.getSettings(file).getCommonSettings(JavaLanguage.INSTANCE);
      final boolean old = settings.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE;
      settings.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = false;
      PsiElement leaf = file.findElementAt(caretOffset - 1);
      PsiElement elt = BasicJavaAstTreeUtil.toPsi(
        BasicJavaAstTreeUtil.getParentOfType(BasicJavaAstTreeUtil.toNode(leaf), BASIC_CODE_BLOCK));
      if (elt == null &&
          leaf != null &&
          leaf.getParent() != null &&
          BasicJavaAstTreeUtil.is(leaf.getParent().getNode(), CLASS_SET)) {
        elt = leaf.getParent();
      }
      reformatAndMove(editor, elt, caretOffset);
      settings.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = old;

      reformatBlockParentIfNeeded(editor, file);
    }
  }

  protected void reformatAndMove(@NotNull Editor editor, @Nullable PsiElement elt, int caretOffset) {
    reformat(elt);
    editor.getCaretModel().moveToOffset(caretOffset - 1);
  }

  private void reformatBlockParentIfNeeded(@NotNull Editor editor, @NotNull PsiFile file) {
    commit(editor);
    ASTNode block =
      BasicJavaAstTreeUtil.findElementOfClassAtOffset(file, editor.getCaretModel().getOffset(), BASIC_CODE_BLOCK, false);
    if (block != null &&
        BasicJavaAstTreeUtil.is(block.getTreeParent(), BASIC_BLOCK_STATEMENT) &&
        BasicJavaAstTreeUtil.is(block.getTreeParent().getTreeParent(), BASIC_FOR_STATEMENT)) {
      reformat(block.getTreeParent().getTreeParent().getPsi());
    }
    if (block != null && BasicJavaAstTreeUtil.is(block.getTreeParent(), BASIC_SWITCH_EXPRESSION)) {
      reformat(block.getTreeParent().getPsi());
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

  protected static void plainEnter(final @NotNull Editor editor) {
    getEnterHandler().execute(editor, editor.getCaretModel().getCurrentCaret(), EditorUtil.getEditorDataContext(editor));
  }

  protected static EditorActionHandler getEnterHandler() {
    return EditorActionManager.getInstance().getActionHandler(IdeActions.ACTION_EDITOR_START_NEW_LINE);
  }

  protected static boolean isModified(final @NotNull Editor editor) {
    final Long timestamp = editor.getUserData(SMART_ENTER_TIMESTAMP);
    return timestamp != null && editor.getDocument().getModificationStamp() != timestamp.longValue();
  }
}
