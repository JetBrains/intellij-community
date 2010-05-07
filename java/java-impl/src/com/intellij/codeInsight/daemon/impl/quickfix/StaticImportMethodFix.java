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
import com.intellij.codeInsight.completion.JavaCompletionUtil;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.ide.util.MethodCellRenderer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.proximity.PsiProximityComparator;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Vector;

public class StaticImportMethodFix implements IntentionAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.StaticImportMethodFix");
  private final SmartPsiElementPointer<PsiMethodCallExpression> myMethodCall;
  private List<PsiMethod> candidates;
  private static final int OPTIONS = PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_CONTAINING_CLASS  | PsiFormatUtil.SHOW_FQ_NAME;

  public StaticImportMethodFix(@NotNull PsiMethodCallExpression methodCallExpression) {
    myMethodCall = SmartPointerManager.getInstance(methodCallExpression.getProject()).createSmartPsiElementPointer(methodCallExpression);
  }

  @NotNull
  public String getText() {
    String text = QuickFixBundle.message("static.import.method.text");
    if (candidates.size() == 1) {
      text += " '" + PsiFormatUtil.formatMethod(candidates.get(0), PsiSubstitutor.EMPTY, OPTIONS, 0)+"'";
    }
    else {
      text += "...";
    }
    return text;
  }

  @NotNull
  public String getFamilyName() {
    return getText();
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return PsiUtil.isLanguageLevel5OrHigher(file)
           && myMethodCall != null
           && myMethodCall.getElement() != null
           && myMethodCall.getElement().isValid()
           && myMethodCall.getElement().getMethodExpression().getQualifierExpression() == null
           && file.getManager().isInProject(file)
           && !(candidates == null ? candidates = getMethodsToImport() : candidates).isEmpty()
      ;
  }

  @NotNull
  private List<PsiMethod> getMethodsToImport() {
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(myMethodCall.getProject());
    PsiShortNamesCache cache = facade.getShortNamesCache();
    PsiMethodCallExpression element = myMethodCall.getElement();
    PsiReferenceExpression reference = element.getMethodExpression();
    PsiExpressionList argumentList = element.getArgumentList();
    String name = reference.getReferenceName();
    ArrayList<PsiMethod> list = new ArrayList<PsiMethod>();
    if (name == null) return list;
    GlobalSearchScope scope = element.getResolveScope();
    PsiMethod[] methods = cache.getMethodsByNameIfNotMoreThan(name, scope, 20);
    List<PsiMethod> applicableList = new ArrayList<PsiMethod>();
    for (PsiMethod method : methods) {
      ProgressManager.checkCanceled();
      PsiClass aClass = method.getContainingClass();
      if (aClass != null && JavaCompletionUtil.isInExcludedPackage(aClass)) continue;
      if (!method.hasModifierProperty(PsiModifier.STATIC)) continue;
      PsiFile file = method.getContainingFile();
      if (file instanceof PsiJavaFile
          //do not show methods from default package
          && ((PsiJavaFile)file).getPackageName().length() != 0
          && PsiUtil.isAccessible(method, element, aClass)) {
        list.add(method);
        if (PsiUtil.isApplicable(method, PsiSubstitutor.EMPTY, argumentList)) {
          applicableList.add(method);
        }
      }
    }
    List<PsiMethod> result = applicableList.isEmpty() ? list : applicableList;
    for (int i = result.size() - 1; i >= 0; i--) {
      PsiMethod method = result.get(i);
      PsiClass containingClass = method.getContainingClass();
      for (int j = i+1; j<result.size() ;j++) {
        PsiMethod exMethod = result.get(j);
        if (!Comparing.strEqual(exMethod.getName(), method.getName())) continue;
        PsiClass exContainingClass = exMethod.getContainingClass();
        if (containingClass != null && exContainingClass != null
            && !Comparing.equal(containingClass.getQualifiedName(), exContainingClass.getQualifiedName())) continue;
        // same named methods, drop one
        result.remove(i);
        break;
      }
    }
    Collections.sort(result, new PsiProximityComparator(argumentList));
    return result;
  }

  public void invoke(@NotNull final Project project, final Editor editor, PsiFile file) {
    if (!CodeInsightUtilBase.prepareFileForWrite(file)) return;
    if (candidates.size() == 1) {
      final PsiMethod toImport = candidates.get(0);
      doImport(toImport);
    }
    else {
      chooseAndImport(editor);
    }
  }

  private void doImport(final PsiMethod toImport) {
    CommandProcessor.getInstance().executeCommand(toImport.getProject(), new Runnable(){
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            try {
              PsiMethodCallExpression element = myMethodCall.getElement();
              if (element != null) {
                element.getMethodExpression().bindToElementViaStaticImport(toImport.getContainingClass());
              }
            }
            catch (IncorrectOperationException e) {
              LOG.error(e);
            }
          }
        });

      }
    }, getText(), this);

  }

  private void chooseAndImport(Editor editor) {
    final JList list = new JList(new Vector<PsiMethod>(candidates));
    list.setCellRenderer(new MethodCellRenderer(true, OPTIONS));
    new PopupChooserBuilder(list).
      setTitle(QuickFixBundle.message("static.import.method.choose.method.to.import")).
      setMovable(true).
      setItemChoosenCallback(new Runnable() {
        public void run() {
          PsiMethod selectedValue = (PsiMethod)list.getSelectedValue();
          if (selectedValue == null) return;
          LOG.assertTrue(selectedValue.isValid());
          doImport(selectedValue);
        }
      }).createPopup().
      showInBestPositionFor(editor);
  }

  public boolean startInWriteAction() {
    return true;
  }
}
