package com.intellij.codeInsight.completion.methodChains.completion.lookup;

import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Batkovich <dmitry.batkovich@jetbrains.com>
 */
public class ChainCompletionNewVariableLookupElement extends LookupItem<PsiClass> {

  private final PsiClass psiClass;
  private final String newVarName;

  public ChainCompletionNewVariableLookupElement(final PsiClass psiClass, final String newVarName) {
    super(psiClass, newVarName);
    this.newVarName = newVarName;
    this.psiClass = psiClass;
  }

  public static ChainCompletionNewVariableLookupElement create(final PsiClass psiClass) {
    final Project project = psiClass.getProject();
    final SuggestedNameInfo suggestedNameInfo = JavaCodeStyleManager.getInstance(project).suggestVariableName(VariableKind.LOCAL_VARIABLE, null, null, JavaPsiFacade .getElementFactory( project).createType(psiClass));
    return new ChainCompletionNewVariableLookupElement(psiClass, chooseLongest(suggestedNameInfo.names));
  }

  @Override
  public void handleInsert(final InsertionContext context) {
    final PsiFile file = context.getFile();
    ((PsiJavaFile) file).importClass(psiClass);
    final PsiStatement statement = PsiTreeUtil.getParentOfType(file.findElementAt(context.getEditor().getCaretModel().getOffset()), PsiStatement.class);
    final PsiCodeBlock codeBlock = PsiTreeUtil.getParentOfType(statement, PsiCodeBlock.class);
    assert codeBlock != null;
    final Project project = context.getProject();
    new WriteCommandAction.Simple(project, file) {
      @Override
      protected void run() throws Throwable {
        codeBlock.addBefore(
            JavaPsiFacade.getElementFactory(
                project).
                createStatementFromText(String.format("%s %s = null;", psiClass.getName(), newVarName), null), statement);
      }
    }.execute();
    PsiDocumentManager.getInstance(context.getProject()).doPostponedOperationsAndUnblockDocument(context.getDocument());
  }

  @NotNull
  @Override
  public String getLookupString() {
    return newVarName;
  }

  @Override
  public void renderElement(final LookupElementPresentation presentation) {
    super.renderElement(presentation);
    presentation.setItemText(newVarName);
  }

  private static String chooseLongest(final String[] names) {
    String longestWord = names[0];
    int maxLength = longestWord.length();
    for (int i = 1; i < names.length; i++) {
      final int length = names[i].length();
      if (length > maxLength) {
        maxLength = length;
        longestWord = names[i];
      }
    }
    return longestWord;
  }
}
