package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;

public class AddMethodFix implements IntentionAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.AddMethodFix");

  private final PsiClass myClass;
  private final PsiMethod myMethod;
  private String myText;
  private final List<String> myExceptions = new ArrayList<String>();

  public AddMethodFix(@NotNull PsiMethod method, @NotNull PsiClass implClass) {
    myMethod = method;
    myClass = implClass;
    setText(QuickFixBundle.message("add.method.text", method.getName(), implClass.getName()));
  }

  public AddMethodFix(@NonNls @NotNull String methodText, @NotNull PsiClass implClass, @NotNull String... exceptions) {
    this(createMethod(methodText, implClass), implClass);
    myExceptions.addAll(Arrays.asList(exceptions));
  }

  private static PsiMethod createMethod(final String methodText, final PsiClass implClass) {
    try {
      return implClass.getManager().getElementFactory().createMethodFromText(methodText, implClass);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
      return null;
    }
  }

  private static PsiMethod reformat(Project project, PsiMethod result) throws IncorrectOperationException {
    CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
    result = (PsiMethod) codeStyleManager.shortenClassReferences(result);
    result = (PsiMethod) codeStyleManager.reformat(result);
    return result;
  }

  protected void setText(@NotNull String text) {
    myText = text;
  }

  @NotNull
  public String getText() {
    return myText;
  }

  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("add.method.family");
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return myMethod != null
           && myMethod.isValid()
           && myClass != null
           && myClass.isValid()
           && myClass.getManager().isInProject(myClass)
           && myText != null
           && MethodSignatureUtil.findMethodBySignature(myClass, myMethod, false) == null
        ;
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    if (!CodeInsightUtil.prepareFileForWrite(myClass.getContainingFile())) return;
    PsiCodeBlock body;
    if (myClass.isInterface() && (body = myMethod.getBody()) != null) body.delete();
    PsiMethod method = (PsiMethod)myClass.add(myMethod);
    for (String exception : myExceptions) {
      PsiUtil.addException(myMethod, exception);
    }
    method = (PsiMethod)method.replace(reformat(project, method));
    GenerateMembersUtil.positionCaret(editor, method, true);
  }

  public boolean startInWriteAction() {
    return true;
  }
}
