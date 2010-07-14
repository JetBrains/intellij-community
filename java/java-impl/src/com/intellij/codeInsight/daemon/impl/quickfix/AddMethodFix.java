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
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.codeInspection.IntentionAndQuickFixAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class AddMethodFix extends IntentionAndQuickFixAction {
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
    ContainerUtil.addAll(myExceptions, exceptions);
  }

  private static PsiMethod createMethod(final String methodText, final PsiClass implClass) {
    try {
      return JavaPsiFacade.getInstance(implClass.getProject()).getElementFactory().createMethodFromText(methodText, implClass);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
      return null;
    }
  }

  private static PsiMethod reformat(Project project, PsiMethod result) throws IncorrectOperationException {
    CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
    result = (PsiMethod) codeStyleManager.reformat(result);

    JavaCodeStyleManager javaCodeStyleManager = JavaCodeStyleManager.getInstance(project);
    result = (PsiMethod) javaCodeStyleManager.shortenClassReferences(result);
    return result;
  }

  protected void setText(@NotNull String text) {
    myText = text;
  }

  @NotNull
  public String getName() {
    return myText;
  }

  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("add.method.family");
  }

  public boolean isAvailable(@NotNull final Project project, final Editor editor, final PsiFile file) {
    return myMethod != null
           && myMethod.isValid()
           && myClass != null
           && myClass.isValid()
           && myClass.getManager().isInProject(myClass)
           && myText != null
           && MethodSignatureUtil.findMethodBySignature(myClass, myMethod, false) == null
        ;
  }

  public void applyFix(final Project project, final PsiFile file, final Editor editor) {
    if (!CodeInsightUtilBase.prepareFileForWrite(myClass.getContainingFile())) return;
    PsiCodeBlock body;
    if (myClass.isInterface() && (body = myMethod.getBody()) != null) body.delete();
    for (String exception : myExceptions) {
      PsiUtil.addException(myMethod, exception);
    }
    PsiMethod method = (PsiMethod)myClass.add(myMethod);
    method = (PsiMethod)method.replace(reformat(project, method));
    if (editor != null) {
      GenerateMembersUtil.positionCaret(editor, method, true);
    }
  }
}
