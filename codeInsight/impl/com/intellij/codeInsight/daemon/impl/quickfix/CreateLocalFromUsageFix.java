package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.impl.TypeExpression;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateBuilder;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mike
 */
public class CreateLocalFromUsageFix extends CreateVarFromUsageFix {

  public CreateLocalFromUsageFix(PsiReferenceExpression referenceExpression) {
    super(referenceExpression);
  }

  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.CreateLocalFromUsageFix");

  public String getText(String varName) {
    return QuickFixBundle.message("create.local.from.usage.text", varName);
  }

  protected boolean isAvailableImpl(int offset) {
    if (!super.isAvailableImpl(offset)) return false;
    if(myReferenceExpression.isQualified()) return false;
    PsiElement scope = PsiTreeUtil.getParentOfType(myReferenceExpression, PsiModifierListOwner.class);
    return scope instanceof PsiMethod || scope instanceof PsiClassInitializer ||
           scope instanceof PsiLocalVariable || scope instanceof PsiAnonymousClass;
  }

  protected void invokeImpl(PsiClass targetClass) {
    if (CreateFromUsageUtils.isValidReference(myReferenceExpression, true)) {
      return;
    }

    PsiManager psiManager = myReferenceExpression.getManager();
    Project project = psiManager.getProject();
    PsiElementFactory factory = psiManager.getElementFactory();

    PsiFile targetFile = targetClass.getContainingFile();

    try {
      PsiType[] expectedTypes = CreateFromUsageUtils.guessType(myReferenceExpression, false);
      PsiType type = expectedTypes[0];

      String varName = myReferenceExpression.getReferenceName();
      PsiExpression initializer = null;
      boolean isInline = false;
      PsiExpression[] expressions = CreateFromUsageUtils.collectExpressions(myReferenceExpression, PsiMember.class, PsiFile.class);
      PsiStatement anchor = getAnchor(expressions);
      if (anchor instanceof PsiExpressionStatement && ((PsiExpressionStatement) anchor).getExpression() instanceof PsiAssignmentExpression) {
        PsiAssignmentExpression assignment = (PsiAssignmentExpression) ((PsiExpressionStatement) anchor).getExpression();
        if (assignment.getLExpression().textMatches(myReferenceExpression)) {
          initializer = assignment.getRExpression();
          isInline = true;
        }
      }

      PsiDeclarationStatement decl = factory.createVariableDeclarationStatement(varName, type, initializer);

      TypeExpression expression = new TypeExpression(project, expectedTypes);

      if (isInline) {
        decl = (PsiDeclarationStatement)anchor.replace(decl);
      }
      else {
        decl = (PsiDeclarationStatement)anchor.getParent().addBefore(decl, anchor);
      }

      PsiVariable var = (PsiVariable)decl.getDeclaredElements()[0];
      boolean isFinal = CodeStyleSettingsManager.getSettings(project).GENERATE_FINAL_LOCALS && !CreateFromUsageUtils.isAccessedForWriting(expressions);
      var.getModifierList().setModifierProperty(PsiModifier.FINAL, isFinal);

      var = CodeInsightUtil.forcePsiPostprocessAndRestoreElement(var);
      if (var == null) return;
      TemplateBuilder builder = new TemplateBuilder(var);
      builder.replaceElement(var.getTypeElement(), expression);
      builder.setEndVariableAfter(var.getNameIdentifier());
      Template template = builder.buildTemplate();

      Editor newEditor = positionCursor(project, targetFile, var);
      TextRange range = var.getTextRange();
      newEditor.getDocument().deleteString(range.getStartOffset(), range.getEndOffset());

      TemplateManager manager = TemplateManager.getInstance(project);
      manager.startTemplate(newEditor, template);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  protected boolean isAllowOuterTargetClass() {
    return false;
  }

  private static PsiStatement getAnchor(PsiExpression[] expressionOccurences) {
    PsiElement parent = expressionOccurences[0];
    int minOffset = expressionOccurences[0].getTextRange().getStartOffset();
    for (int i = 1; i < expressionOccurences.length; i++) {
      parent = PsiTreeUtil.findCommonParent(parent, expressionOccurences[i]);
      LOG.assertTrue(parent != null);
      minOffset = Math.min(minOffset, expressionOccurences[i].getTextRange().getStartOffset());
    }

    PsiCodeBlock block = (PsiCodeBlock) (parent instanceof PsiCodeBlock ? parent : PsiTreeUtil.getParentOfType(parent, PsiCodeBlock.class));
    LOG.assertTrue(block != null && block.getStatements().length > 0);
    PsiStatement[] statements = block.getStatements();
    for (int i = 1; i < statements.length; i++) {
      if (statements[i].getTextRange().getStartOffset() > minOffset) return statements[i-1];
    }
    return statements[statements.length - 1];
  }

  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("create.local.from.usage.family");
  }

}
