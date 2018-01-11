/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.refactoring.replaceConstructorWithBuilder;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.ide.util.PackageUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.*;
import com.intellij.refactoring.MoveDestination;
import com.intellij.refactoring.replaceConstructorWithBuilder.usageInfo.ReplaceConstructorWithSettersChainInfo;
import com.intellij.refactoring.util.FixableUsageInfo;
import com.intellij.refactoring.util.FixableUsagesRefactoringProcessor;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.VisibilityUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author anna
 * @since 04-Sep-2008
 */
public class ReplaceConstructorWithBuilderProcessor extends FixableUsagesRefactoringProcessor {
  public static final String REFACTORING_NAME = "Replace Constructor with Builder";
  private final PsiMethod[] myConstructors;
  private final Map<String, ParameterData> myParametersMap;
  private final String myClassName;
  private final String myPackageName;
  private final boolean myCreateNewBuilderClass;
  private final PsiElementFactory myElementFactory;
  private final MoveDestination myMoveDestination;


  public ReplaceConstructorWithBuilderProcessor(Project project,
                                                PsiMethod[] constructors,
                                                Map<String, ParameterData> parametersMap,
                                                String className,
                                                String packageName,
                                                MoveDestination moveDestination, boolean createNewBuilderClass) {
    super(project);
    myMoveDestination = moveDestination;
    myElementFactory = JavaPsiFacade.getInstance(myProject).getElementFactory();
    myConstructors = constructors;
    myParametersMap = parametersMap;

    myClassName = className;
    myPackageName = packageName;
    myCreateNewBuilderClass = createNewBuilderClass;
  }

  @NotNull
  protected UsageViewDescriptor createUsageViewDescriptor(@NotNull final UsageInfo[] usages) {
    return new ReplaceConstructorWithBuilderViewDescriptor();
  }

  protected void findUsages(@NotNull final List<FixableUsageInfo> usages) {
    final String builderQualifiedName = StringUtil.getQualifiedName(myPackageName, myClassName);
    final PsiClass builderClass =
      JavaPsiFacade.getInstance(myProject).findClass(builderQualifiedName, GlobalSearchScope.projectScope(myProject));

    for (PsiMethod constructor : myConstructors) {
      for (PsiReference reference : ReferencesSearch.search(constructor)) {
        final PsiElement element = reference.getElement();
        final PsiNewExpression newExpression = PsiTreeUtil.getParentOfType(element, PsiNewExpression.class);
        if (newExpression != null && !PsiTreeUtil.isAncestor(builderClass, element, false)) {
          usages.add(new ReplaceConstructorWithSettersChainInfo(newExpression, StringUtil.getQualifiedName(myPackageName, myClassName), myParametersMap));
        }
      }
    }
  }

  @Nullable
  private PsiClass createBuilderClass() {
    final PsiClass psiClass = myConstructors[0].getContainingClass();
    assert psiClass != null;
    final PsiTypeParameterList typeParameterList = psiClass.getTypeParameterList();
    final String text = "public class " + myClassName + (typeParameterList != null ? typeParameterList.getText() : "") + "{}";
    final PsiFileFactory factory = PsiFileFactory.getInstance(myProject);
    final PsiJavaFile newFile = (PsiJavaFile)factory.createFileFromText(myClassName + ".java", JavaFileType.INSTANCE, text);

    final PsiFile containingFile = myConstructors[0].getContainingFile();
    final PsiDirectory containingDirectory = containingFile.getContainingDirectory();
    final PsiDirectory directory;
    if (myMoveDestination != null) {
      directory = myMoveDestination.getTargetDirectory(containingDirectory);
    } else {
      final Module module = ModuleUtilCore.findModuleForPsiElement(containingFile);
      assert module != null;
      directory = PackageUtil.findOrCreateDirectoryForPackage(module, myPackageName, containingDirectory, true, true);
    }

    if (directory != null) {

      final CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(PsiManager.getInstance(myProject).getProject());
      final PsiJavaFile reformattedFile = (PsiJavaFile)codeStyleManager.reformat(JavaCodeStyleManager.getInstance(newFile.getProject()).shortenClassReferences(newFile));

      if (directory.findFile(reformattedFile.getName()) != null) return reformattedFile.getClasses()[0];
      return ((PsiJavaFile)directory.add(reformattedFile)).getClasses()[0];
    }
    return null;
  }

  @Override
  protected void performRefactoring(@NotNull UsageInfo[] usageInfos) {

    final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(myProject);
    final PsiClass builderClass = myCreateNewBuilderClass
                                  ? createBuilderClass()
                                  : psiFacade.findClass(StringUtil.getQualifiedName(myPackageName, myClassName),
                                                        GlobalSearchScope.projectScope(myProject));
    if (builderClass == null) return;

    for (String propertyName : myParametersMap.keySet()) {
      final ParameterData parameterData = myParametersMap.get(propertyName);
      final PsiField field = createField(builderClass, parameterData);
      createSetter(builderClass, parameterData, field);
    }

    super.performRefactoring(usageInfos);

    final PsiMethod method = createMethodSignature(createMethodName());
    if (builderClass.findMethodBySignature(method, false) == null) {
      builderClass.add(method);
    }

    //fix visibilities
    final PsiMethod constructor = getWorkingConstructor();
    VisibilityUtil.escalateVisibility(constructor, builderClass);
    PsiClass containingClass = constructor.getContainingClass();
    while (containingClass != null) {
      VisibilityUtil.escalateVisibility(containingClass, builderClass);
      containingClass = containingClass.getContainingClass();
    }
  }

  private void createSetter(PsiClass builderClass, ParameterData parameterData, PsiField field) {
    PsiMethod setter = null;
    for (PsiMethod method : builderClass.getMethods()) {
      if (Comparing.strEqual(method.getName(), parameterData.getSetterName()) && method.getParameterList().getParametersCount() == 1
          && TypeConversionUtil.isAssignable(method.getParameterList().getParameters()[0].getType(), parameterData.getType())) {
        setter = method;
        fixSetterReturnType(builderClass, field, setter);
        break;
      }
    }
    if (setter == null) {
      setter = PropertyUtilBase.generateSetterPrototype(field, builderClass, true);
      final PsiIdentifier nameIdentifier = setter.getNameIdentifier();
      assert nameIdentifier != null;
      nameIdentifier.replace(myElementFactory.createIdentifier(parameterData.getSetterName()));
      setter.getParameterList().getParameters()[0].getTypeElement().replace(myElementFactory.createTypeElement(parameterData.getType())); //setter varargs
      builderClass.add(setter);
    }
  }

  private PsiField createField(PsiClass builderClass, ParameterData parameterData) {
    PsiField field = builderClass.findFieldByName(parameterData.getFieldName(), false);

    if (field == null) {
      PsiType type = parameterData.getType();
      if (type instanceof PsiEllipsisType) {
        type = ((PsiEllipsisType)type).toArrayType();
      }
      field = myElementFactory.createField(parameterData.getFieldName(), type);
      field = (PsiField)builderClass.add(field);
    }

    final String defaultValue = parameterData.getDefaultValue();
    if (defaultValue != null) {
      final PsiExpression initializer = field.getInitializer();
      if (initializer == null) {
        try {
          field.setInitializer(myElementFactory.createExpressionFromText(defaultValue, field));
        }
        catch (IncorrectOperationException e) {
          //skip invalid default value
        }
      }
    }
    return field;
  }

  private void fixSetterReturnType(PsiClass builderClass, PsiField field, PsiMethod method) {
    if (PsiUtil.resolveClassInType(method.getReturnType()) != builderClass) {
      final PsiCodeBlock body = method.getBody();
      final PsiCodeBlock generatedBody = PropertyUtilBase.generateSetterPrototype(field, builderClass, true).getBody();
      assert body != null;
      assert generatedBody != null;
      body.replace(generatedBody);
      final PsiTypeElement typeElement = method.getReturnTypeElement();
      assert typeElement != null;
      typeElement.replace(myElementFactory.createTypeElement(myElementFactory.createType(builderClass)));
    }
  }

  private PsiMethod createMethodSignature(String createMethodName) {
    JavaCodeStyleManager styleManager = JavaCodeStyleManager.getInstance(myProject);
    final StringBuffer buf = new StringBuffer();
    final PsiMethod constructor = getWorkingConstructor();
    for (PsiParameter parameter : constructor.getParameterList().getParameters()) {
      final String pureParamName = styleManager.variableNameToPropertyName(parameter.getName(), VariableKind.PARAMETER);
      if (buf.length() > 0) buf.append(", ");
      buf.append(myParametersMap.get(pureParamName).getFieldName());
    }
    return myElementFactory.createMethodFromText("public " +
                                               constructor.getName() +
                                               " " +
                                               createMethodName +
                                               "(){\n return new " +
                                               constructor.getName() +
                                               "(" +
                                               buf.toString() +
                                               ")" +
                                               ";\n}", constructor);
  }

  private PsiMethod getWorkingConstructor() {
    PsiMethod constructor = getMostCommonConstructor();
    if (constructor == null){
      constructor = myConstructors[0];
      if (constructor.getParameterList().getParametersCount() == 0) {
        constructor = myConstructors[1];
      }
    }
    return constructor;
  }

  @Nullable
  private PsiMethod getMostCommonConstructor() {
    if (myConstructors.length == 1) return myConstructors[0];
    PsiMethod commonConstructor = null;
    for (PsiMethod constructor : myConstructors) {
      final PsiMethod chainedConstructor = RefactoringUtil.getChainedConstructor(constructor);
      if (chainedConstructor == null) {
        if (commonConstructor != null) {
          if (!isChained(commonConstructor, constructor)) {
            return null;
          }
        }
        commonConstructor = constructor;
      } else {
        if (commonConstructor == null) {
          commonConstructor = chainedConstructor;
        } else {
          if (!isChained(commonConstructor, chainedConstructor)) {
            return null;
          }
        }
      }
    }
    return commonConstructor;
  }

  private static boolean isChained(PsiMethod first, PsiMethod last) {
    if (first == null) return false;
    if (first == last) return true;
    return isChained(RefactoringUtil.getChainedConstructor(first), last);
  }

  private String createMethodName() {
    return "create" + StringUtil.capitalize(myConstructors[0].getName());
  }

  
  @Override
  protected boolean preprocessUsages(@NotNull Ref<UsageInfo[]> refUsages) {
    final MultiMap<PsiElement, String> conflicts = new MultiMap<>();
    final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(myProject);
    final PsiClass builderClass =
      psiFacade.findClass(StringUtil.getQualifiedName(myPackageName, myClassName), GlobalSearchScope.projectScope(myProject));
    if (builderClass == null) {
      if (!myCreateNewBuilderClass) {
        conflicts.putValue(null, "Selected class was not found.");
      }
    } else if (myCreateNewBuilderClass){
      conflicts.putValue(builderClass, "Class with chosen name already exist.");
    }
    
    if (myMoveDestination != null && myCreateNewBuilderClass) {
      myMoveDestination.analyzeModuleConflicts(Collections.emptyList(), conflicts, refUsages.get());
    }

    final PsiMethod commonConstructor = getMostCommonConstructor();
    if (commonConstructor == null) {
      conflicts.putValue(null, "Found constructors are not reducible to simple chain");
    }

    return showConflicts(conflicts, refUsages.get());
  }

  @NotNull
  protected String getCommandName() {
    return REFACTORING_NAME;
  }
}
