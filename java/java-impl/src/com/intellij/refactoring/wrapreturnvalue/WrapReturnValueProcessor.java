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
package com.intellij.refactoring.wrapreturnvalue;

import com.intellij.ide.util.PackageUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.RefactorJBundle;
import com.intellij.refactoring.psi.PropertyUtils;
import com.intellij.refactoring.psi.TypeParametersVisitor;
import com.intellij.refactoring.util.FixableUsageInfo;
import com.intellij.refactoring.util.FixableUsagesRefactoringProcessor;
import com.intellij.refactoring.wrapreturnvalue.usageInfo.ChangeReturnType;
import com.intellij.refactoring.wrapreturnvalue.usageInfo.ReturnWrappedValue;
import com.intellij.refactoring.wrapreturnvalue.usageInfo.UnwrapCall;
import com.intellij.refactoring.wrapreturnvalue.usageInfo.WrapReturnValue;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class WrapReturnValueProcessor extends FixableUsagesRefactoringProcessor {

  private static final Logger LOG = Logger.getInstance("com.siyeh.rpp.wrapreturnvalue.WrapReturnValueProcessor");

  private final PsiMethod method;
  private final String className;
  private final String packageName;
  private final boolean myCreateInnerClass;
  private final PsiField myDelegateField;
  private final String myQualifiedName;
  private final boolean myUseExistingClass;
  private final List<PsiTypeParameter> typeParams;
  @NonNls
  private final String unwrapMethodName;

  public WrapReturnValueProcessor(String className,
                                  String packageName,
                                  PsiMethod method,
                                  boolean useExistingClass,
                                  final boolean createInnerClass, PsiField delegateField) {
    super(method.getProject());
    this.method = method;
    this.className = className;
    this.packageName = packageName;
    myCreateInnerClass = createInnerClass;
    myDelegateField = delegateField;
    myQualifiedName = StringUtil.getQualifiedName(packageName, className);
    this.myUseExistingClass = useExistingClass;

    final Set<PsiTypeParameter> typeParamSet = new HashSet<PsiTypeParameter>();
    final TypeParametersVisitor visitor = new TypeParametersVisitor(typeParamSet);
    final PsiTypeElement returnTypeElement = method.getReturnTypeElement();
    assert returnTypeElement != null;
    returnTypeElement.accept(visitor);
    typeParams = new ArrayList<PsiTypeParameter>(typeParamSet);
    if (useExistingClass) {
      unwrapMethodName = calculateUnwrapMethodName();
    }
    else {
      unwrapMethodName = "getValue";
    }
  }

  private String calculateUnwrapMethodName() {
    final PsiClass existingClass = JavaPsiFacade.getInstance(myProject).findClass(myQualifiedName, GlobalSearchScope.allScope(myProject));
    if (existingClass != null) {
      if (TypeConversionUtil.isPrimitiveWrapper(myQualifiedName)) {
        final PsiPrimitiveType unboxedType =
          PsiPrimitiveType.getUnboxedType(JavaPsiFacade.getInstance(myProject).getElementFactory().createType(existingClass));
        assert unboxedType != null;
        return unboxedType.getCanonicalText() + "Value()";
      }

      final PsiMethod getter = PropertyUtils.findGetterForField(myDelegateField);
      return getter != null ? getter.getName() : "";
    }
    return "";
  }

  protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo[] usageInfos) {
    return new WrapReturnValueUsageViewDescriptor(method, usageInfos);
  }

  public void findUsages(@NotNull List<FixableUsageInfo> usages) {
    findUsagesForMethod(method, usages);
    for (PsiMethod overridingMethod : OverridingMethodsSearch.search(method)) {
      findUsagesForMethod(overridingMethod, usages);
    }
  }

  private void findUsagesForMethod(PsiMethod psiMethod, List<FixableUsageInfo> usages) {
    for (PsiReference reference : ReferencesSearch.search(psiMethod, psiMethod.getUseScope())) {
      final PsiElement referenceElement = reference.getElement();
      final PsiElement parent = referenceElement.getParent();
      if (parent instanceof PsiCallExpression) {
        usages.add(new UnwrapCall((PsiCallExpression)parent, unwrapMethodName));
      }
    }
    final String returnType = calculateReturnTypeString();
    usages.add(new ChangeReturnType(psiMethod, returnType));
    psiMethod.accept(new ReturnSearchVisitor(usages, returnType, psiMethod));
  }

  private String calculateReturnTypeString() {
    final String qualifiedName = StringUtil.getQualifiedName(packageName, className);
    final StringBuilder returnTypeBuffer = new StringBuilder(qualifiedName);
    if (!typeParams.isEmpty()) {
      returnTypeBuffer.append('<');
      returnTypeBuffer.append(StringUtil.join(typeParams, new Function<PsiTypeParameter, String>() {
        public String fun(final PsiTypeParameter typeParameter) {
          final String paramName = typeParameter.getName();
          LOG.assertTrue(paramName != null);
          return paramName;
        }
      }, ","));
      returnTypeBuffer.append('>');
    }
    return returnTypeBuffer.toString();
  }

  @Override
  protected boolean preprocessUsages(final Ref<UsageInfo[]> refUsages) {
    MultiMap<PsiElement, String> conflicts = new MultiMap<PsiElement, String>();
    final PsiClass existingClass = JavaPsiFacade.getInstance(myProject).findClass(myQualifiedName, GlobalSearchScope.allScope(myProject));
    if (myUseExistingClass) {
      if (existingClass == null) {
        conflicts.putValue(existingClass, RefactorJBundle.message("could.not.find.selected.wrapping.class"));
      }
      else {
        boolean foundConstructor = false;
        final Set<PsiType> returnTypes = new HashSet<PsiType>();
        returnTypes.add(method.getReturnType());
        final PsiCodeBlock methodBody = method.getBody();
        if (methodBody != null) {
          methodBody.accept(new JavaRecursiveElementWalkingVisitor() {
            @Override
            public void visitReturnStatement(final PsiReturnStatement statement) {
              super.visitReturnStatement(statement);
              if (PsiTreeUtil.getParentOfType(statement, PsiMethod.class) != method) return;
              final PsiExpression returnValue = statement.getReturnValue();
              if (returnValue != null) {
                returnTypes.add(returnValue.getType());
              }
            }
          });
        }

        final PsiMethod[] constructors = existingClass.getConstructors();
        constr: for (PsiMethod constructor : constructors) {
          final PsiParameter[] parameters = constructor.getParameterList().getParameters();
          if (parameters.length == 1) {
            final PsiParameter parameter = parameters[0];
            final PsiType parameterType = parameter.getType();
            for (PsiType returnType : returnTypes) {
              if (!TypeConversionUtil.isAssignable(parameterType, returnType)) {
                continue constr;
              }
            }
            final PsiCodeBlock body = constructor.getBody();
            LOG.assertTrue(body != null);
            final boolean[] found = new boolean[1];
            body.accept(new JavaRecursiveElementWalkingVisitor() {
              @Override
              public void visitAssignmentExpression(final PsiAssignmentExpression expression) {
                super.visitAssignmentExpression(expression);
                final PsiExpression lExpression = expression.getLExpression();
                if (lExpression instanceof PsiReferenceExpression && ((PsiReferenceExpression)lExpression).resolve() == myDelegateField) {
                  final PsiExpression rExpression = expression.getRExpression();
                  if (rExpression instanceof PsiReferenceExpression && ((PsiReferenceExpression)rExpression).resolve() == parameter) {
                    found[0] = true;
                  }
                }
              }
            });
            if (found[0]) {
              foundConstructor = true;
              break;
            }
          }
        }
        if (!foundConstructor) {
          conflicts.putValue(existingClass, "Existing class does not have appropriate constructor");
        }
      }
      if (unwrapMethodName.length() == 0) {
        conflicts.putValue(existingClass,
                      "Existing class does not have getter for selected field");
      }
    }
    else {
      if (existingClass != null) {
        conflicts.putValue(existingClass, RefactorJBundle.message("there.already.exists.a.class.with.the.selected.name"));
      }
    }
    return showConflicts(conflicts, refUsages.get());
  }

  protected void performRefactoring(UsageInfo[] usageInfos) {
    if (!myUseExistingClass && !buildClass()) return;
    super.performRefactoring(usageInfos);
  }

  private boolean buildClass() {
    final PsiManager manager = method.getManager();
    final Project project = method.getProject();
    final ReturnValueBeanBuilder beanClassBuilder = new ReturnValueBeanBuilder();
    beanClassBuilder.setCodeStyleSettings(project);
    beanClassBuilder.setTypeArguments(typeParams);
    beanClassBuilder.setClassName(className);
    beanClassBuilder.setPackageName(packageName);
    beanClassBuilder.setStatic(myCreateInnerClass && method.hasModifierProperty(PsiModifier.STATIC));
    final PsiType returnType = method.getReturnType();
    beanClassBuilder.setValueType(returnType);

    final String classString;
    try {
      classString = beanClassBuilder.buildBeanClass();
    }
    catch (IOException e) {
      LOG.error(e);
      return false;
    }

    try {
      final PsiJavaFile psiFile = (PsiJavaFile)PsiFileFactory.getInstance(project).createFileFromText(className + ".java", classString);
      final CodeStyleManager codeStyleManager = manager.getCodeStyleManager();
      if (myCreateInnerClass) {
        final PsiClass containingClass = method.getContainingClass();
        final PsiElement innerClass = containingClass.add(psiFile.getClasses()[0]);
        JavaCodeStyleManager.getInstance(project).shortenClassReferences(innerClass);
      } else {
        final PsiFile containingFile = method.getContainingFile();

        final PsiDirectory containingDirectory = containingFile.getContainingDirectory();
        final Module module = ModuleUtil.findModuleForPsiElement(containingFile);
        final PsiDirectory directory = PackageUtil.findOrCreateDirectoryForPackage(module, packageName, containingDirectory, true);

        if (directory != null) {
          final PsiElement shortenedFile = JavaCodeStyleManager.getInstance(project).shortenClassReferences(psiFile);
          final PsiElement reformattedFile = codeStyleManager.reformat(shortenedFile);
          directory.add(reformattedFile);
        } else {
          return false;
        }
      }
    }
    catch (IncorrectOperationException e) {
      LOG.info(e);
      return false;
    }
    return true;
  }

  protected String getCommandName() {
    final PsiClass containingClass = method.getContainingClass();
    return RefactorJBundle.message("wrapped.return.command.name", className, containingClass.getName(), '.', method.getName());
  }


  private class ReturnSearchVisitor extends JavaRecursiveElementWalkingVisitor {
    private final List<FixableUsageInfo> usages;
    private final String type;
    private final PsiMethod myMethod;

    ReturnSearchVisitor(List<FixableUsageInfo> usages, String type, final PsiMethod psiMethod) {
      super();
      this.usages = usages;
      this.type = type;
      myMethod = psiMethod;
    }

    public void visitReturnStatement(PsiReturnStatement statement) {
      super.visitReturnStatement(statement);

      if (PsiTreeUtil.getParentOfType(statement, PsiMethod.class) != myMethod) return;

      final PsiExpression returnValue = statement.getReturnValue();
      if (myUseExistingClass && returnValue instanceof PsiMethodCallExpression) {
        final PsiMethodCallExpression callExpression = (PsiMethodCallExpression)returnValue;
        if (callExpression.getArgumentList().getExpressions().length == 0) {
          final PsiReferenceExpression callMethodExpression = callExpression.getMethodExpression();
          final String methodName = callMethodExpression.getReferenceName();
          if (Comparing.strEqual(unwrapMethodName, methodName)) {
            final PsiExpression qualifier = callMethodExpression.getQualifierExpression();
            if (qualifier != null) {
              final PsiType qualifierType = qualifier.getType();
              if (qualifierType != null && qualifierType.getCanonicalText().equals(myQualifiedName)) {
                usages.add(new ReturnWrappedValue(statement));
                return;
              }
            }
          }
        }
      }
      usages.add(new WrapReturnValue(statement, type));
    }
  }
}
