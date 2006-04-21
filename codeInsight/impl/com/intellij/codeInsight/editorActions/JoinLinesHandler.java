/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: May 20, 2002
 * Time: 6:21:42 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInsight.editorActions;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;

public class JoinLinesHandler extends EditorWriteActionHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.editorActions.JoinLinesHandler");
  private EditorActionHandler myOriginalHandler;

  public JoinLinesHandler(EditorActionHandler originalHandler) {
    myOriginalHandler = originalHandler;
  }

  public void executeWriteAction(final Editor editor, final DataContext dataContext) {
    final DocumentEx doc = (DocumentEx) editor.getDocument();
    final Project project = (Project) DataManager.getInstance().getDataContext(editor.getContentComponent()).getData(DataConstants.PROJECT);

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        LogicalPosition caretPosition = editor.getCaretModel().getLogicalPosition();

        PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(doc);

        if (psiFile == null) {
          myOriginalHandler.execute(editor, dataContext);
          return;
        }

        int startLine = caretPosition.line;
        int endLine = startLine + 1;
        if (editor.getSelectionModel().hasSelection()) {
          startLine = doc.getLineNumber(editor.getSelectionModel().getSelectionStart());
          endLine = doc.getLineNumber(editor.getSelectionModel().getSelectionEnd());
          if (doc.getLineStartOffset(endLine) == editor.getSelectionModel().getSelectionEnd()) endLine--;
        }

        int caretRestoreOffset = -1;
        for (int i = startLine; i < endLine; i++) {
          if (i >= doc.getLineCount() - 1) break;
          int lineEndOffset = doc.getLineEndOffset(startLine);

          PsiDocumentManager.getInstance(project).commitDocument(doc);
          CharSequence text = doc.getCharsSequence();
          int firstNonSpaceOffsetInNextLine = doc.getLineStartOffset(startLine + 1);
          while (text.charAt(firstNonSpaceOffsetInNextLine) == ' ' || text.charAt(firstNonSpaceOffsetInNextLine) == '\t') firstNonSpaceOffsetInNextLine++;
          PsiElement elementAtNextLineStart = psiFile.findElementAt(firstNonSpaceOffsetInNextLine);
          boolean isNextLineStartsWithComment = elementAtNextLineStart instanceof PsiComment ||
                                                elementAtNextLineStart != null &&
                                                PsiTreeUtil.getParentOfType(
                                                    elementAtNextLineStart,
                                                    PsiDocComment.class
                                                ) != null;

          int lastNonSpaceOffsetInStartLine = lineEndOffset;
          while (lastNonSpaceOffsetInStartLine > 0 &&
                 (text.charAt(lastNonSpaceOffsetInStartLine - 1) == ' ' || text.charAt(lastNonSpaceOffsetInStartLine - 1) == '\t')) {
            lastNonSpaceOffsetInStartLine--;
          }
          int elemOffset = lastNonSpaceOffsetInStartLine > doc.getLineStartOffset(startLine)
                           ? lastNonSpaceOffsetInStartLine - 1
                           : -1;
          PsiElement elementAtStartLineEnd = elemOffset == -1 ? null : psiFile.findElementAt(elemOffset);
          boolean isStartLineEndsWithComment = elementAtStartLineEnd instanceof PsiComment ||
                                               elementAtStartLineEnd != null &&
                                               PsiTreeUtil.getParentOfType(elementAtStartLineEnd, PsiDocComment.class) != null;

          if (lastNonSpaceOffsetInStartLine == doc.getLineStartOffset(startLine)) {
            doc.deleteString(doc.getLineStartOffset(startLine), firstNonSpaceOffsetInNextLine);

            int indent = -1;
            try {
              PsiDocumentManager.getInstance(project).commitDocument(doc);
              indent = CodeStyleManager.getInstance(project).adjustLineIndent(
                  psiFile,
                  startLine == 0 ? 0 : doc.getLineStartOffset(startLine)
              );
            }
            catch (IncorrectOperationException e) {
              LOG.error(e);
            }

            if (caretRestoreOffset == -1) {
              caretRestoreOffset = indent;
            }

            continue;
          }

          doc.deleteString(lineEndOffset, lineEndOffset + doc.getLineSeparatorLength(startLine));

          text = doc.getCharsSequence();
          int start = lineEndOffset-1;
          int end = lineEndOffset;
          while (start > 0 && (text.charAt(start) == ' ' || text.charAt(start) == '\t')) start--;
          while (end < doc.getTextLength() && (text.charAt(end) == ' ' || text.charAt(end) == '\t')) end++;


          // Check if we're joining splitted string literal.
          PsiDocumentManager.getInstance(project).commitDocument(doc);
          int rc = tryJoinStringLiteral(doc, psiFile, start);

          if (rc == -1) {
            PsiElement psiAtStartLineEnd = psiFile.findElementAt(start);
            PsiElement psiAtNextLineStart = psiFile.findElementAt(end);
            rc = tryJoinDeclaration(psiAtStartLineEnd, psiAtNextLineStart);
            if (rc == -1) {
              rc = tryUnwrapBlockStatement(psiAtStartLineEnd, psiAtNextLineStart);
            }
          }

          if (rc != -1) {
            if (caretRestoreOffset == -1) caretRestoreOffset = rc;
            continue;
          }

          if (caretRestoreOffset == -1) caretRestoreOffset = start == lineEndOffset ? start : start + 1;


          if (isStartLineEndsWithComment && isNextLineStartsWithComment) {
            if (text.charAt(end) == '*' && end < text.length() && text.charAt(end + 1) != '/') {
              end++;
              while (end < doc.getTextLength() && (text.charAt(end) == ' ' || text.charAt(end) == '\t')) end++;
            } else if (text.charAt(end) == '/') {
              end += 2;
              while (end < doc.getTextLength() && (text.charAt(end) == ' ' || text.charAt(end) == '\t')) end++;
            }

            doc.replaceString(start == lineEndOffset ? start : start + 1, end, " ");
            continue;
          }

          while (end < doc.getTextLength() && (text.charAt(end) == ' ' || text.charAt(end) == '\t')) end++;
          doc.replaceString(start == lineEndOffset ? start : start + 1, end, " ");

          if (start <= doc.getLineStartOffset(startLine)) {
            try {
              PsiDocumentManager.getInstance(project).commitDocument(doc);
              CodeStyleManager.getInstance(project).adjustLineIndent(psiFile, doc.getLineStartOffset(startLine));
            } catch (IncorrectOperationException e) {
              LOG.error(e);
            }
          }

          int prevLineCount = doc.getLineCount();

          PsiDocumentManager.getInstance(project).commitDocument(doc);
          try {
            CodeStyleManager.getInstance(project).reformatText(psiFile, start+1, end);
          } catch (IncorrectOperationException e) {
            LOG.error(e);
          }

          if (prevLineCount < doc.getLineCount()) {
            end = doc.getLineEndOffset(startLine) + doc.getLineSeparatorLength(startLine);
            start = end - doc.getLineSeparatorLength(startLine);
            int addedLinesCount = doc.getLineCount() - prevLineCount - 1;
            while (end < doc.getTextLength() &&
                   (text.charAt(end) == ' ' || text.charAt(end) == '\t' || text.charAt(end) == '\n' && addedLinesCount > 0)) {
              if (text.charAt(end) == '\n') addedLinesCount--;
              end++;
            }
            doc.replaceString(start, end, " ");
          }

          PsiDocumentManager.getInstance(project).commitDocument(doc);
        }

        if (editor.getSelectionModel().hasSelection()) {
          editor.getCaretModel().moveToOffset(editor.getSelectionModel().getSelectionEnd());
        } else if (caretRestoreOffset != -1) {
          editor.getCaretModel().moveToOffset(caretRestoreOffset);
          editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
          editor.getSelectionModel().removeSelection();
        }
      }
    });
  }

  private static int tryUnwrapBlockStatement(PsiElement elementAtStartLineEnd, PsiElement elementAtNextLineStart) {
    if (elementAtStartLineEnd == null || elementAtNextLineStart == null) return -1;
    if (!(elementAtStartLineEnd instanceof PsiJavaToken) || ((PsiJavaToken)elementAtStartLineEnd).getTokenType() != JavaTokenType.LBRACE) {
      return -1;
    }
    final PsiElement codeBlock = elementAtStartLineEnd.getParent();
    if (!(codeBlock instanceof PsiCodeBlock)) return -1;
    if (!(codeBlock.getParent() instanceof PsiBlockStatement)) return -1;
    final PsiElement parentStatement = codeBlock.getParent().getParent();

    final CodeStyleSettings codeStyleSettings = CodeStyleSettingsManager.getSettings(elementAtStartLineEnd.getProject());
    if (!(parentStatement instanceof PsiIfStatement && codeStyleSettings.IF_BRACE_FORCE != CodeStyleSettings.FORCE_BRACES_ALWAYS ||
             parentStatement instanceof PsiWhileStatement && codeStyleSettings.WHILE_BRACE_FORCE != CodeStyleSettings.FORCE_BRACES_ALWAYS ||
           (parentStatement instanceof PsiForStatement || parentStatement instanceof PsiForeachStatement) &&
           codeStyleSettings.FOR_BRACE_FORCE != CodeStyleSettings.FORCE_BRACES_ALWAYS ||
             parentStatement instanceof PsiDoWhileStatement &&
           codeStyleSettings.DOWHILE_BRACE_FORCE != CodeStyleSettings.FORCE_BRACES_ALWAYS)) {
      return -1;
    }
    PsiElement foundStatement = null;
    for (PsiElement element = elementAtStartLineEnd.getNextSibling(); element != null; element = element.getNextSibling()) {
      if (element instanceof PsiWhiteSpace) continue;
      if (element instanceof PsiJavaToken && ((PsiJavaToken)element).getTokenType() == JavaTokenType.RBRACE && element.getParent() == codeBlock) {
        if (foundStatement == null) return -1;
        break;
      }
      if (foundStatement != null) return -1;
      foundStatement = element;
    }
    try {
      final PsiElement newStatement = codeBlock.getParent().replace(foundStatement);
      return newStatement.getTextRange().getStartOffset();
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
    return -1;
  }
  private static int tryJoinDeclaration(PsiElement elementAtStartLineEnd, PsiElement elementAtNextLineStart) {
    if (elementAtStartLineEnd == null || elementAtNextLineStart == null) return -1;

    // first line.
    if (!(elementAtStartLineEnd instanceof PsiJavaToken)) return -1;
    PsiJavaToken lastFirstLineToken = (PsiJavaToken) elementAtStartLineEnd;
    if (lastFirstLineToken.getTokenType() != JavaTokenType.SEMICOLON) return -1;
    if (!(lastFirstLineToken.getParent() instanceof PsiLocalVariable)) return -1;
    PsiLocalVariable var = (PsiLocalVariable) lastFirstLineToken.getParent();

    if (!(var.getParent() instanceof PsiDeclarationStatement)) return -1;
    PsiDeclarationStatement decl = (PsiDeclarationStatement) var.getParent();
    if (decl.getDeclaredElements().length > 1) return -1;

    //second line.
    if (!(elementAtNextLineStart instanceof PsiJavaToken)) return -1;
    PsiJavaToken firstNextLineToken = (PsiJavaToken) elementAtNextLineStart;
    if (firstNextLineToken.getTokenType() != JavaTokenType.IDENTIFIER) return -1;
    if (!(firstNextLineToken.getParent() instanceof PsiReferenceExpression)) return -1;
    PsiReferenceExpression ref = (PsiReferenceExpression) firstNextLineToken.getParent();
    PsiElement refResolved = ref.resolve();

    PsiManager psiManager = ref.getManager();
    if (!psiManager.areElementsEquivalent(refResolved, var)) return -1;
    if (!(ref.getParent() instanceof PsiAssignmentExpression)) return -1;
    PsiAssignmentExpression assignment = (PsiAssignmentExpression) ref.getParent();
    if (!(assignment.getParent() instanceof PsiExpressionStatement)) return -1;

    if (psiManager.getSearchHelper().findReferences(var, new LocalSearchScope(assignment.getRExpression()), false).length > 0) {
      return -1;
    }

    final PsiElementFactory factory = psiManager.getElementFactory();
    PsiExpression initializerExpression;
    final IElementType originalOpSign = assignment.getOperationSign().getTokenType();
    if (originalOpSign == JavaTokenType.EQ) {
      initializerExpression = assignment.getRExpression();
    }
    else {
      if (var.getInitializer() == null) return -1;
      String opSign = null;
      if (originalOpSign == JavaTokenType.ANDEQ) {
        opSign = "&";
      }
      else if (originalOpSign == JavaTokenType.ASTERISKEQ) {
        opSign = "*";
      }
      else if (originalOpSign == JavaTokenType.DIVEQ) {
        opSign = "/";
      }
      else if (originalOpSign == JavaTokenType.GTGTEQ) {
        opSign = ">>";
      }
      else if (originalOpSign == JavaTokenType.GTGTGTEQ) {
        opSign = ">>>";
      }
      else if (originalOpSign == JavaTokenType.LTLTEQ) {
        opSign = "<<";
      }
      else if (originalOpSign == JavaTokenType.MINUSEQ) {
        opSign = "-";
      }
      else if (originalOpSign == JavaTokenType.OREQ) {
        opSign = "|";
      }
      else if (originalOpSign == JavaTokenType.PERCEQ) {
        opSign = "%";
      }
      else if (originalOpSign == JavaTokenType.PLUSEQ) {
        opSign = "+";
      }
      else if (originalOpSign == JavaTokenType.XOREQ) {
        opSign = "^";
      }

      try {
        initializerExpression = factory.createExpressionFromText(var.getInitializer().getText() + opSign + assignment.getRExpression().getText(), var);
        initializerExpression = (PsiExpression)CodeStyleManager.getInstance(psiManager).reformat(initializerExpression);
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
        return -1;
      }
    }

    PsiExpressionStatement statement = (PsiExpressionStatement) assignment.getParent();

    int startOffset = decl.getTextRange().getStartOffset();
    try {
      PsiDeclarationStatement newDecl = factory.createVariableDeclarationStatement(
          var.getName(), var.getType(),
          initializerExpression
      );
      PsiVariable newVar = (PsiVariable)newDecl.getDeclaredElements()[0];
      if (var.getModifierList().getText().length() > 0) {
        newVar.getModifierList().setModifierProperty(PsiModifier.FINAL, true);
      }
      newVar.getModifierList().replace(var.getModifierList());
      PsiVariable variable = (PsiVariable)newDecl.getDeclaredElements()[0];
      final int offsetBeforeEQ = variable.getNameIdentifier().getTextRange().getEndOffset();
      final int offsetAfterEQ = variable.getInitializer().getTextRange().getStartOffset() + 1;
      newDecl = (PsiDeclarationStatement)CodeStyleManager.getInstance(psiManager).reformatRange(newDecl,
                                                                                                              offsetBeforeEQ, 
                                                                                                              offsetAfterEQ);     
      
      
      decl.replace(newDecl);
      statement.delete();
      return startOffset + newDecl.getTextRange().getEndOffset() - newDecl.getTextRange().getStartOffset();
    } catch (IncorrectOperationException e) {
      LOG.error(e);
      return -1;
    }
  }

  private static int tryJoinStringLiteral(Document doc, PsiFile psiFile, int offsetNear) {
    CharSequence text = doc.getCharsSequence();
    int state = 0;
    int startQuoteOffset = -1;

    int start = offsetNear;
    while (text.charAt(start) == ' ' || text.charAt(start) == '\t' || text.charAt(start) == '+') start--;
    if (text.charAt(start) == '\"') start--;
    if (start < offsetNear) start++;

state_loop:
    for (int j = start; j < doc.getTextLength(); j++) {
      switch (text.charAt(j)) {
        case ' ':
        case '\t':
          break;

        case '\"':
          if (state == 0) {
            state = 1;
            startQuoteOffset = j;
            PsiElement psiAtOffset = psiFile.findElementAt(j);
            if (!(psiAtOffset instanceof PsiJavaToken)) return -1;
            if (((PsiJavaToken)psiAtOffset).getTokenType() != JavaTokenType.STRING_LITERAL) return -1;
            break;
          }

          if (state == 2) {
            doc.deleteString(startQuoteOffset, j + 1);
            return startQuoteOffset;
          }
          break state_loop;

        case '+':
          if (state != 1) break state_loop;
          state = 2;
          break;

        default: break state_loop;
      }
    }

    return -1;
  }
}
