package com.intellij.codeInsight.editorActions;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;

public class DeclarationJoinLinesHandler implements JoinLinesHandlerDelegate {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.editorActions.DeclarationJoinLinesHandler");

  public int tryJoinLines(final DocumentEx document, final PsiFile file, final int start, final int end) {
    PsiElement elementAtStartLineEnd = file.findElementAt(start);
    PsiElement elementAtNextLineStart = file.findElementAt(end);
    if (elementAtStartLineEnd == null || elementAtNextLineStart == null) return -1;

    // first line.
    if (!(elementAtStartLineEnd instanceof PsiJavaToken)) return -1;
    PsiJavaToken lastFirstLineToken = (PsiJavaToken)elementAtStartLineEnd;
    if (lastFirstLineToken.getTokenType() != JavaTokenType.SEMICOLON) return -1;
    if (!(lastFirstLineToken.getParent() instanceof PsiLocalVariable)) return -1;
    PsiLocalVariable var = (PsiLocalVariable)lastFirstLineToken.getParent();

    if (!(var.getParent() instanceof PsiDeclarationStatement)) return -1;
    PsiDeclarationStatement decl = (PsiDeclarationStatement)var.getParent();
    if (decl.getDeclaredElements().length > 1) return -1;

    //second line.
    if (!(elementAtNextLineStart instanceof PsiJavaToken)) return -1;
    PsiJavaToken firstNextLineToken = (PsiJavaToken)elementAtNextLineStart;
    if (firstNextLineToken.getTokenType() != JavaTokenType.IDENTIFIER) return -1;
    if (!(firstNextLineToken.getParent() instanceof PsiReferenceExpression)) return -1;
    PsiReferenceExpression ref = (PsiReferenceExpression)firstNextLineToken.getParent();
    PsiElement refResolved = ref.resolve();

    PsiManager psiManager = ref.getManager();
    if (!psiManager.areElementsEquivalent(refResolved, var)) return -1;
    if (!(ref.getParent() instanceof PsiAssignmentExpression)) return -1;
    PsiAssignmentExpression assignment = (PsiAssignmentExpression)ref.getParent();
    if (!(assignment.getParent() instanceof PsiExpressionStatement)) return -1;

    if (ReferencesSearch.search(var, new LocalSearchScope(assignment.getRExpression()), false).toArray(new PsiReference[0]).length > 0) {
      return -1;
    }

    final PsiElementFactory factory = JavaPsiFacade.getInstance(psiManager.getProject()).getElementFactory();
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
        initializerExpression =
          factory.createExpressionFromText(var.getInitializer().getText() + opSign + assignment.getRExpression().getText(), var);
        initializerExpression = (PsiExpression)CodeStyleManager.getInstance(psiManager).reformat(initializerExpression);
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
        return -1;
      }
    }

    PsiExpressionStatement statement = (PsiExpressionStatement)assignment.getParent();

    int startOffset = decl.getTextRange().getStartOffset();
    try {
      PsiDeclarationStatement newDecl = factory.createVariableDeclarationStatement(var.getName(), var.getType(), initializerExpression);
      PsiVariable newVar = (PsiVariable)newDecl.getDeclaredElements()[0];
      if (var.getModifierList().getText().length() > 0) {
        newVar.getModifierList().setModifierProperty(PsiModifier.FINAL, true);
      }
      newVar.getModifierList().replace(var.getModifierList());
      PsiVariable variable = (PsiVariable)newDecl.getDeclaredElements()[0];
      final int offsetBeforeEQ = variable.getNameIdentifier().getTextRange().getEndOffset();
      final int offsetAfterEQ = variable.getInitializer().getTextRange().getStartOffset() + 1;
      newDecl = (PsiDeclarationStatement)CodeStyleManager.getInstance(psiManager).reformatRange(newDecl, offsetBeforeEQ, offsetAfterEQ);


      decl.replace(newDecl);
      statement.delete();
      return startOffset + newDecl.getTextRange().getEndOffset() - newDecl.getTextRange().getStartOffset();
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
      return -1;
    }
  }
}
