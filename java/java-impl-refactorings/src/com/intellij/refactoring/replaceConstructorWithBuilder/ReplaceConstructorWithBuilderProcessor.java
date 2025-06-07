// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.replaceConstructorWithBuilder;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.ide.util.EditorHelper;
import com.intellij.ide.util.PackageUtil;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.MoveDestination;
import com.intellij.refactoring.replaceConstructorWithBuilder.usageInfo.ReplaceConstructorWithSettersChainInfo;
import com.intellij.refactoring.util.FixableUsageInfo;
import com.intellij.refactoring.util.FixableUsagesRefactoringProcessor;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.util.CommonJavaRefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.VisibilityUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * @author anna
 */
public class ReplaceConstructorWithBuilderProcessor extends FixableUsagesRefactoringProcessor {
  private final PsiMethod[] myConstructors;
  private final Map<String, ParameterData> myParametersMap;
  private final @NotNull @NlsSafe String myClassName;
  private final @NlsSafe String myPackageName;
  private final boolean myCreateNewBuilderClass;
  private final PsiElementFactory myElementFactory;
  private final MoveDestination myMoveDestination;
  private final boolean myOpenInEditor;

  public ReplaceConstructorWithBuilderProcessor(Project project,
                                                PsiMethod[] constructors,
                                                Map<String, ParameterData> parametersMap,
                                                @NotNull String className,
                                                String packageName,
                                                MoveDestination moveDestination, 
                                                boolean createNewBuilderClass,
                                                boolean openInEditor) {
    super(project);
    myElementFactory = JavaPsiFacade.getElementFactory(myProject);
    myConstructors = constructors;
    myParametersMap = parametersMap;
    myClassName = className;
    myPackageName = packageName;
    myMoveDestination = moveDestination;
    myCreateNewBuilderClass = createNewBuilderClass;
    myOpenInEditor = openInEditor;
  }

  @Override
  protected @NotNull UsageViewDescriptor createUsageViewDescriptor(final UsageInfo @NotNull [] usages) {
    return new ReplaceConstructorWithBuilderViewDescriptor();
  }

  @Override
  protected void findUsages(final @NotNull List<? super FixableUsageInfo> usages) {
    final String builderQualifiedName = StringUtil.getQualifiedName(myPackageName, myClassName);
    final PsiClass builderClass =
      JavaPsiFacade.getInstance(myProject).findClass(builderQualifiedName, GlobalSearchScope.projectScope(myProject));

    for (PsiMethod constructor : myConstructors) {
      for (PsiReference reference : ReferencesSearch.search(constructor).asIterable()) {
        final PsiElement element = reference.getElement();
        final PsiNewExpression newExpression = PsiTreeUtil.getParentOfType(element, PsiNewExpression.class);
        if (newExpression != null && !PsiTreeUtil.isAncestor(builderClass, element, false) && newExpression.getAnonymousClass() == null) {
          usages.add(new ReplaceConstructorWithSettersChainInfo(newExpression, StringUtil.getQualifiedName(myPackageName, myClassName), myParametersMap));
        }
      }
    }
  }

  private @Nullable PsiClass createBuilderClass() {
    final PsiClass psiClass = myConstructors[0].getContainingClass();
    assert psiClass != null;
    final PsiTypeParameterList typeParameterList = psiClass.getTypeParameterList();
    final String text = "public class " + myClassName + "{}";
    final PsiFileFactory factory = PsiFileFactory.getInstance(myProject);
    final PsiJavaFile newFile = (PsiJavaFile)factory.createFileFromText(myClassName + ".java", JavaFileType.INSTANCE, text);
    PsiTypeParameterList list = newFile.getClasses()[0].getTypeParameterList();
    if (list != null && typeParameterList != null) list.replace(typeParameterList);

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
  protected void performRefactoring(UsageInfo @NotNull [] usageInfos) {
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

    final PsiMethod method = createBuildMethod(createMethodName());
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
    
    if (myOpenInEditor) {
      EditorHelper.openInEditor(builderClass);
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

  private PsiMethod createBuildMethod(String createMethodName) {
    JavaCodeStyleManager styleManager = JavaCodeStyleManager.getInstance(myProject);
    
    final PsiClass aClass = myConstructors[0].getContainingClass();
    assert aClass != null;
    PsiType[] typeArguments = Stream.of(aClass.getTypeParameters())
      .map(myElementFactory::createType)
      .toArray(PsiType[]::new);
    PsiMethod method = myElementFactory.createMethod(createMethodName, myElementFactory.createType(aClass, typeArguments));
    
    final StringBuilder bodyText = new StringBuilder();
    final PsiMethod constructor = getWorkingConstructor();
    bodyText.append("{\n  return new ").append(constructor.getName());
    if (typeArguments.length > 0) {
      if (PsiUtil.isAvailable(JavaFeature.DIAMOND_TYPES, aClass)) {
        bodyText.append("<>");
      }
      else {
        bodyText.append('<');
        boolean comma = false;
        for (PsiType argument : typeArguments) {
          bodyText.append(comma ? ", " : "").append(argument.getCanonicalText());
          comma = true;
        }
        bodyText.append('>');
      }
    }
    bodyText.append("("); 
    boolean comma = false;
    for (PsiParameter parameter : constructor.getParameterList().getParameters()) {
      final String pureParamName = styleManager.variableNameToPropertyName(parameter.getName(), VariableKind.PARAMETER);
      bodyText.append(comma ? ", " : "").append(myParametersMap.get(pureParamName).getFieldName());
      comma = true;
    }
    bodyText.append(");\n}");
    PsiCodeBlock body = myElementFactory.createCodeBlockFromText(bodyText.toString(), method);
    Objects.requireNonNull(method.getBody()).replace(body);
    return method;
  }

  private PsiMethod getWorkingConstructor() {
    PsiMethod constructor = getMostCommonConstructor();
    if (constructor == null){
      constructor = myConstructors[0];
      if (constructor.getParameterList().isEmpty()) {
        constructor = myConstructors[1];
      }
    }
    return constructor;
  }

  private @Nullable PsiMethod getMostCommonConstructor() {
    if (myConstructors.length == 1) return myConstructors[0];
    PsiMethod commonConstructor = null;
    for (PsiMethod constructor : myConstructors) {
      final PsiMethod chainedConstructor = CommonJavaRefactoringUtil.getChainedConstructor(constructor);
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
    return isChained(CommonJavaRefactoringUtil.getChainedConstructor(first), last);
  }

  private @NlsSafe String createMethodName() {
    return "create" + StringUtil.capitalize(myConstructors[0].getName());
  }

  @Override
  protected boolean preprocessUsages(@NotNull Ref<UsageInfo[]> refUsages) {
    final MultiMap<PsiElement, @NlsContexts.DialogMessage String> conflicts = new MultiMap<>();
    if (myMoveDestination != null && myCreateNewBuilderClass) {
      myMoveDestination.analyzeModuleConflicts(Collections.emptyList(), conflicts, refUsages.get());
    }

    final PsiMethod commonConstructor = getMostCommonConstructor();
    if (commonConstructor == null) {
      PsiClass containingClass = myConstructors[0].getContainingClass();
      assert containingClass != null;
      conflicts.putValue(containingClass, JavaRefactoringBundle.message("replace.constructor.builder.error.no.constructor.chain", 
                                                                          RefactoringUIUtil.getDescription(containingClass, false)));
    }

    return showConflicts(conflicts, refUsages.get());
  }

  @Override
  protected @NotNull String getCommandName() {
    return JavaRefactoringBundle.message("replace.constructor.with.builder");
  }
}
