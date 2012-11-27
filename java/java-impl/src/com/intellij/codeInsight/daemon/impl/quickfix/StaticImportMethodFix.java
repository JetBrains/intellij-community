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

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.completion.JavaCompletionUtil;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.actions.AddImportAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.impl.AddSingleMemberStaticImportAction;
import com.intellij.ide.util.MethodCellRenderer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.DefaultParameterTypeInferencePolicy;
import com.intellij.psi.impl.source.resolve.PsiResolveHelperImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.util.*;
import com.intellij.psi.util.proximity.PsiProximityComparator;
import com.intellij.ui.popup.list.ListPopupImpl;
import com.intellij.ui.popup.list.PopupListElementRenderer;
import com.intellij.util.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class StaticImportMethodFix implements IntentionAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.StaticImportMethodFix");
  private final SmartPsiElementPointer<PsiMethodCallExpression> myMethodCall;
  private List<PsiMethod> candidates;

  public StaticImportMethodFix(@NotNull PsiMethodCallExpression methodCallExpression) {
    myMethodCall = SmartPointerManager.getInstance(methodCallExpression.getProject()).createSmartPsiElementPointer(methodCallExpression);
  }

  @Override
  @NotNull
  public String getText() {
    String text = QuickFixBundle.message("static.import.method.text");
    if (candidates != null && candidates.size() == 1) {
      text += " '" + PsiFormatUtil.formatMethod(candidates.get(0), PsiSubstitutor.EMPTY, PsiFormatUtilBase.SHOW_NAME |
                                                                                         PsiFormatUtilBase.SHOW_CONTAINING_CLASS |
                                                                                         PsiFormatUtilBase.SHOW_FQ_NAME, 0)+"'";
    }
    else {
      text += "...";
    }
    return text;
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return getText();
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return PsiUtil.isLanguageLevel5OrHigher(file)
           && myMethodCall.getElement() != null
           && myMethodCall.getElement().isValid()
           && myMethodCall.getElement().getMethodExpression().getQualifierExpression() == null
           && file.getManager().isInProject(file)
           && !(candidates == null ? candidates = getMethodsToImport() : candidates).isEmpty()
      ;
  }

  private PsiType getExpectedType() {
    final PsiMethodCallExpression methodCall = myMethodCall.getElement();
    if (methodCall == null) return null;
    final PsiElement parent = PsiResolveHelperImpl.skipParenthesizedExprUp(methodCall.getParent());

    if (parent instanceof PsiVariable) {
      if (methodCall.equals(PsiResolveHelperImpl.skipParenthesizedExprDown(((PsiVariable)parent).getInitializer()))) {
        return ((PsiVariable)parent).getType();
      }
    }
    else if (parent instanceof PsiAssignmentExpression) {
      if (methodCall.equals(PsiResolveHelperImpl.skipParenthesizedExprDown(((PsiAssignmentExpression)parent).getRExpression()))) {
        return ((PsiAssignmentExpression)parent).getLExpression().getType();
      }
    }
    else if (parent instanceof PsiReturnStatement) {
      final PsiLambdaExpression lambdaExpression = PsiTreeUtil.getParentOfType(parent, PsiLambdaExpression.class);
      if (lambdaExpression != null) {
        return LambdaUtil.getFunctionalInterfaceReturnType(lambdaExpression.getFunctionalInterfaceType());
      }
      else {
        PsiMethod method = PsiTreeUtil.getParentOfType(parent, PsiMethod.class);
        if (method != null) {
          return method.getReturnType();
        }
      }
    }
    else if (parent instanceof PsiExpressionList) {
      final PsiElement pParent = parent.getParent();
      if (pParent instanceof PsiCallExpression && parent.equals(((PsiCallExpression)pParent).getArgumentList())) {
        final JavaResolveResult resolveResult = ((PsiCallExpression)pParent).resolveMethodGenerics();
        final PsiElement psiElement = resolveResult.getElement();
        if (psiElement instanceof PsiMethod) {
          final PsiMethod psiMethod = (PsiMethod)psiElement;
          final PsiParameter[] parameters = psiMethod.getParameterList().getParameters();
          final int idx = ArrayUtilRt.find(((PsiExpressionList)parent).getExpressions(), PsiResolveHelperImpl.skipParenthesizedExprUp(methodCall));
          if (idx > -1 && parameters.length > 0) {
            PsiType parameterType = parameters[Math.min(idx, parameters.length - 1)].getType();
            if (idx >= parameters.length - 1) {
              final PsiParameter lastParameter = parameters[parameters.length - 1];
              if (lastParameter.isVarArgs()) {
                parameterType = ((PsiEllipsisType)lastParameter.getType()).getComponentType();
              }
            }
            return resolveResult.getSubstitutor().substitute(parameterType);
          }
          else {
            return null;
          }
        }
      }
    }
    else if (parent instanceof PsiLambdaExpression) {
      return LambdaUtil.getFunctionalInterfaceReturnType(((PsiLambdaExpression)parent).getFunctionalInterfaceType());
    }
    return null;
  }
  
  @NotNull
  private List<PsiMethod> getMethodsToImport() {
    PsiShortNamesCache cache = PsiShortNamesCache.getInstance(myMethodCall.getProject());
    final PsiMethodCallExpression element = myMethodCall.getElement();
    PsiReferenceExpression reference = element.getMethodExpression();
    final PsiExpressionList argumentList = element.getArgumentList();
    String name = reference.getReferenceName();
    final List<PsiMethod> list = new ArrayList<PsiMethod>();
    if (name == null) return list;
    GlobalSearchScope scope = element.getResolveScope();

    final PsiType expectedType = getExpectedType();
    final List<PsiMethod> applicableList = new ArrayList<PsiMethod>();
    final PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(element.getProject()).getResolveHelper();
    cache.processMethodsWithName(name, scope, new Processor<PsiMethod>() {
      @Override
      public boolean process(PsiMethod method) {
        ProgressManager.checkCanceled();
        if (JavaCompletionUtil.isInExcludedPackage(method, false)
            || !method.hasModifierProperty(PsiModifier.STATIC)) return true;
        PsiFile file = method.getContainingFile();
        if (file instanceof PsiJavaFile
            //do not show methods from default package
            && !((PsiJavaFile)file).getPackageName().isEmpty()
            && PsiUtil.isAccessible(method, element, method.getContainingClass())) {
          list.add(method);
          PsiSubstitutor substitutorForMethod = resolveHelper
            .inferTypeArguments(method.getTypeParameters(), method.getParameterList().getParameters(), argumentList.getExpressions(),
                                PsiSubstitutor.EMPTY, element.getParent(), DefaultParameterTypeInferencePolicy.INSTANCE);
          if (PsiUtil.isApplicable(method, substitutorForMethod, argumentList)) {
            if (expectedType == null || TypeConversionUtil.isAssignable(expectedType, substitutorForMethod.substitute(method.getReturnType()))) {
              applicableList.add(method);
            }
          }
        }
        return (applicableList.isEmpty() ? list : applicableList).size() < 50;
      }
    });
    List<PsiMethod> result = applicableList.isEmpty() ? list : applicableList;
    for (int i = result.size() - 1; i >= 0; i--) {
      ProgressManager.checkCanceled();
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
      // check for manually excluded
      if (isExcluded(method)) {
        result.remove(i);
      }
    }
    Collections.sort(result, new PsiProximityComparator(argumentList));
    return result;
  }

  public static boolean isExcluded(PsiMember method) {
    String name = getMemberQualifiedName(method);
    if (name == null) return false;
    CodeInsightSettings cis = CodeInsightSettings.getInstance();
    for (String excluded : cis.EXCLUDED_PACKAGES) {
      if (name.equals(excluded) || name.startsWith(excluded + ".")) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void invoke(@NotNull final Project project, final Editor editor, PsiFile file) {
    if (!CodeInsightUtilBase.prepareFileForWrite(file)) return;
    if (candidates.size() == 1) {
      final PsiMethod toImport = candidates.get(0);
      doImport(toImport);
    }
    else {
      chooseAndImport(editor, project);
    }
  }

  private void doImport(final PsiMethod toImport) {
    CommandProcessor.getInstance().executeCommand(toImport.getProject(), new Runnable(){
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            try {
              PsiMethodCallExpression element = myMethodCall.getElement();
              if (element != null) {
                AddSingleMemberStaticImportAction.bindAllClassRefs(element.getContainingFile(), toImport, toImport.getName(), toImport.getContainingClass());
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

  private void chooseAndImport(Editor editor, final Project project) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      doImport(candidates.get(0));
      return;
    }
    final BaseListPopupStep<PsiMethod> step =
      new BaseListPopupStep<PsiMethod>(QuickFixBundle.message("class.to.import.chooser.title"), candidates) {

        @Override
        public PopupStep onChosen(PsiMethod selectedValue, boolean finalChoice) {
          if (selectedValue == null) {
            return FINAL_CHOICE;
          }

          if (finalChoice) {
            PsiDocumentManager.getInstance(project).commitAllDocuments();
            LOG.assertTrue(selectedValue.isValid());
            doImport(selectedValue);
            return FINAL_CHOICE;
          }

          String qname = getMemberQualifiedName(selectedValue);
          if (qname == null) return FINAL_CHOICE;
          List<String> excludableStrings = AddImportAction.getAllExcludableStrings(qname);
          return new BaseListPopupStep<String>(null, excludableStrings) {
            @NotNull
            @Override
            public String getTextFor(String value) {
              return "Exclude '" + value + "' from auto-import";
            }

            @Override
            public PopupStep onChosen(String selectedValue, boolean finalChoice) {
              if (finalChoice) {
                AddImportAction.excludeFromImport(project, selectedValue);
              }

              return super.onChosen(selectedValue, finalChoice);
            }
          };
        }

        @Override
        public boolean hasSubstep(PsiMethod selectedValue) {
          return true;
        }

        @NotNull
        @Override
        public String getTextFor(PsiMethod value) {
          return ObjectUtils.assertNotNull(value.getName());
        }

        @Override
        public Icon getIconFor(PsiMethod aValue) {
          return aValue.getIcon(0);
        }
      };

    final ListPopupImpl popup = new ListPopupImpl(step) {
      final PopupListElementRenderer rightArrow = new PopupListElementRenderer(this);
      @Override
      protected ListCellRenderer getListElementRenderer() {
        return new MethodCellRenderer(true, PsiFormatUtilBase.SHOW_NAME){
          @Override
          protected DefaultListCellRenderer getRightCellRenderer(final Object value) {
            final DefaultListCellRenderer moduleRenderer = super.getRightCellRenderer(value);
            return new DefaultListCellRenderer(){
              @Override
              public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                JPanel panel = new JPanel(new BorderLayout());
                if (moduleRenderer != null) {
                  Component moduleComponent = moduleRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                  panel.add(moduleComponent, BorderLayout.CENTER);
                }
                rightArrow.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                Component rightArrowComponent = rightArrow.getNextStepLabel();
                panel.add(rightArrowComponent, BorderLayout.EAST);
                return panel;
              }
            };
          }
        };
      }
    };
    popup.showInBestPositionFor(editor);
  }

  @Nullable
  public static String getMemberQualifiedName(PsiMember member) {
    if (member instanceof PsiClass) {
      return ((PsiClass)member).getQualifiedName();
    }

    PsiClass containingClass = member.getContainingClass();
    if (containingClass == null) return null;
    String className = containingClass.getQualifiedName();
    if (className == null) return null;
    return className + "." + member.getName();
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
