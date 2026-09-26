// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.extractMethodObject;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaRecursiveElementVisitor;
import com.intellij.psi.JavaResolveResult;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiImportList;
import com.intellij.psi.PsiImportStatementBase;
import com.intellij.psi.PsiImportStaticStatement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.PsiTypeParameterList;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.extractMethodObject.reflect.PsiReflectionAccessUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class ExtractGeneratedClassUtil {
  private static final Logger LOG = Logger.getInstance(ExtractGeneratedClassUtil.class);

  static PsiClass extractGeneratedClass(@NotNull PsiClass generatedInnerClass,
                                        @NotNull PsiElementFactory elementFactory,
                                        @NotNull PsiElement anchor,
                                        @Nullable String explicitGeneratedEvaluationClassFullName) {
    Project project = generatedInnerClass.getProject();

    if (explicitGeneratedEvaluationClassFullName == null) {
      explicitGeneratedEvaluationClassFullName = "idea.debugger.rt.GeneratedEvaluationClass";
    }

    int dotIndex = explicitGeneratedEvaluationClassFullName.lastIndexOf('.');


    String generatedEvaluationClass = dotIndex == -1 ? explicitGeneratedEvaluationClassFullName : explicitGeneratedEvaluationClassFullName.substring(dotIndex + 1);
    String packageName = dotIndex == -1 ? "" : explicitGeneratedEvaluationClassFullName.substring(0, dotIndex);

    PsiClass extractedClass = elementFactory.createClass(generatedEvaluationClass);
    copyTypeParameters(generatedInnerClass, extractedClass, elementFactory);

    for (PsiField field : generatedInnerClass.getAllFields()) {
      extractedClass.add(elementFactory.createFieldFromText(field.getText(), anchor)); // TODO: check if null is OK
    }

    for (PsiMethod psiMethod : generatedInnerClass.getMethods()) {
      extractedClass.add(elementFactory.createMethodFromText(psiMethod.getText(), anchor));
    }

    PsiJavaFile generatedFile = (PsiJavaFile)PsiFileFactory.getInstance(project)
      .createFileFromText(extractedClass.getName() + ".java", JavaFileType.INSTANCE, extractedClass.getContainingFile().getText());

    generatedFile.setPackageName(packageName);
    extractedClass = PsiTreeUtil.findChildOfType(generatedFile, PsiClass.class);
    assert extractedClass != null;
    copyStaticImports(generatedInnerClass, extractedClass, generatedFile, elementFactory);
    PsiElement codeBlock = PsiTreeUtil.findFirstParent(anchor, false, element -> element instanceof PsiCodeBlock);
    if (codeBlock == null) {
      codeBlock = anchor.getParent();
    }

    addGeneratedClassInfo(codeBlock, generatedInnerClass, extractedClass);
    return extractedClass;
  }

  private static void copyTypeParameters(@NotNull PsiClass generatedInnerClass,
                                         @NotNull PsiClass extractedClass,
                                         @NotNull PsiElementFactory elementFactory) {
    PsiTypeParameterList targetTypeParameters = extractedClass.getTypeParameterList();
    if (targetTypeParameters == null) return;

    Set<String> visibleTypeParameters = new HashSet<>();
    for (PsiTypeParameter typeParameter : PsiUtil.typeParametersIterable(generatedInnerClass)) {
      String name = typeParameter.getName();
      if (name != null && visibleTypeParameters.add(name)) {
        PsiClassType[] accessibleBounds = ContainerUtil.filter(typeParameter.getExtendsListTypes(), PsiReflectionAccessUtil::isAccessibleTypeParameterBound)
          .toArray(PsiClassType.EMPTY_ARRAY);
        targetTypeParameters.add(elementFactory.createTypeParameter(name, accessibleBounds));
      }
    }
  }

  private static void copyStaticImports(@NotNull PsiElement from,
                                        @NotNull PsiElement target,
                                        @NotNull PsiJavaFile destFile,
                                        @NotNull PsiElementFactory elementFactory) {
    PsiJavaFile fromFile = PsiTreeUtil.getParentOfType(from, PsiJavaFile.class);
    if (fromFile != null) {
      List<PsiJavaCodeReferenceElement> references = collectUnqualifiedReferences(target);
      PsiImportList sourceImportList = fromFile.getImportList();
      if (sourceImportList != null) {
        PsiImportList destImportList = destFile.getImportList();
        LOG.assertTrue(destImportList != null, "import list of destination file should not be null");
        Set<PsiJavaCodeReferenceElement> coveredReferences = new HashSet<>();
        for (PsiImportStatementBase importStatement : destImportList.getAllImportStatements()) {
          if (importStatement instanceof PsiImportStaticStatement staticImport) {
            coveredReferences.addAll(findImportedReferences(staticImport, references));
          }
        }
        for (boolean onDemand : new boolean[]{false, true}) {
          for (PsiImportStatementBase importStatement : sourceImportList.getAllImportStatements()) {
            if (importStatement instanceof PsiImportStaticStatement staticImport &&
                staticImport.isOnDemand() == onDemand && isPublic(staticImport)) {
              List<PsiJavaCodeReferenceElement> importedReferences = findImportedReferences(staticImport, references);
              importedReferences.removeAll(coveredReferences);
              if (!importedReferences.isEmpty()) {
                PsiElement importStatementCopy = copy(staticImport, elementFactory);
                if (importStatementCopy != null) {
                  destImportList.add(importStatementCopy);
                  coveredReferences.addAll(importedReferences);
                }
                else {
                  LOG.warn("Unable to copy static import statement: " + importStatement.getText());
                }
              }
            }
          }
        }
      }
    }
  }

  private static @NotNull List<PsiJavaCodeReferenceElement> collectUnqualifiedReferences(@NotNull PsiElement element) {
    List<PsiJavaCodeReferenceElement> references = new ArrayList<>();
    element.accept(new JavaRecursiveElementVisitor() {
      @Override
      public void visitReferenceElement(@NotNull PsiJavaCodeReferenceElement reference) {
        if (reference.getQualifier() == null) {
          references.add(reference);
        }
        super.visitReferenceElement(reference);
      }
    });
    return references;
  }

  private static @NotNull List<PsiJavaCodeReferenceElement> findImportedReferences(
    @NotNull PsiImportStaticStatement staticImport,
    @NotNull List<PsiJavaCodeReferenceElement> references) {
    List<PsiJavaCodeReferenceElement> result = new ArrayList<>();
    if (!staticImport.isOnDemand()) {
      PsiJavaCodeReferenceElement importReference = staticImport.getImportReference();
      if (importReference == null) return result;

      for (JavaResolveResult target : importReference.multiResolve(false)) {
        PsiElement importedElement = target.getElement();
        if (importedElement != null) {
          for (PsiJavaCodeReferenceElement reference : references) {
            if (reference.isReferenceTo(importedElement) && !result.contains(reference)) {
              result.add(reference);
            }
          }
        }
      }
      return result;
    }

    PsiClass importedClass = staticImport.resolveTargetClass();
    if (importedClass == null) return result;

    for (PsiJavaCodeReferenceElement reference : references) {
      PsiElement target = reference.resolve();
      if (target instanceof PsiMember member &&
          member.hasModifierProperty(PsiModifier.PUBLIC) &&
          member.hasModifierProperty(PsiModifier.STATIC)) {
        PsiClass containingClass = member.getContainingClass();
        if (containingClass != null &&
            (importedClass.getManager().areElementsEquivalent(importedClass, containingClass) ||
             importedClass.isInheritor(containingClass, true))) {
          result.add(reference);
        }
      }
    }
    return result;
  }

  private static @Nullable PsiElement copy(@NotNull PsiImportStaticStatement importStatement,
                                            @NotNull PsiElementFactory elementFactory) {
    PsiClass targetClass = importStatement.resolveTargetClass();
    String memberName = importStatement.getReferenceName();
    if (importStatement.isOnDemand()) {
      memberName = "*";
    }
    if (targetClass != null && memberName != null) {
      return elementFactory.createImportStaticStatement(targetClass, memberName);
    }

    return null;
  }

  private static boolean isPublic(@NotNull PsiImportStaticStatement staticImport) {
    PsiClass targetClass = staticImport.resolveTargetClass();
    if (targetClass != null && isPublicClass(targetClass)) {
      if (staticImport.isOnDemand()) {
        return true;
      }
      PsiJavaCodeReferenceElement reference = staticImport.getImportReference();
      if (reference != null) {
        JavaResolveResult[] targets = reference.multiResolve(false);
        for (JavaResolveResult target : targets) {
          PsiElement importedElement = target.getElement();
          if (importedElement instanceof PsiModifierListOwner &&
              ((PsiModifierListOwner)importedElement).hasModifierProperty(PsiModifier.PUBLIC)) {
            return true;
          }
        }
      }
    }
    return false;
  }

  private static boolean isPublicClass(@NotNull PsiClass psiClass) {
    while (psiClass != null) {
      if (!psiClass.hasModifierProperty(PsiModifier.PUBLIC)) {
        return false;
      }
      psiClass = psiClass.getContainingClass();
    }

    return true;
  }

  private static void addGeneratedClassInfo(@NotNull PsiElement element,
                                            @NotNull PsiClass generatedClass,
                                            @NotNull PsiClass extractedClass) {
    generatedClass.putUserData(LightMethodObjectExtractedData.REFERENCED_TYPE, PsiTypesUtil.getClassType(extractedClass));
    element.accept(new JavaRecursiveElementVisitor() {
      @Override
      public void visitNewExpression(@NotNull PsiNewExpression expression) {
        super.visitNewExpression(expression);
        PsiMethod constructor = expression.resolveConstructor();
        if (constructor != null && generatedClass.equals(constructor.getContainingClass())) {
          List<PsiMethod> methods = ContainerUtil.filter(extractedClass.getConstructors(), x -> isSameMethod(x, constructor));
          if (methods.size() == 1) {
            LOG.info("Replace constructor: " + constructor.getName());
            constructor.putUserData(LightMethodObjectExtractedData.REFERENCE_METHOD, methods.get(0));
          }
        }
      }

      @Override
      public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
        super.visitMethodCallExpression(expression);
        PsiMethod method = expression.resolveMethod();
        if (method != null && generatedClass.equals(method.getContainingClass())) {
          List<PsiMethod> methods = ContainerUtil.filter(extractedClass.getMethods(), x -> isSameMethod(x, method));
          if (methods.size() == 1) {
            LOG.info("Replace method: " + method.getName());
            method.putUserData(LightMethodObjectExtractedData.REFERENCE_METHOD, methods.get(0));
          }
        }
      }

      private static boolean isSameMethod(@NotNull PsiMethod first, @NotNull PsiMethod second) {
        if (first.getName().equals(second.getName())) {
          return first.getParameterList().getParametersCount() == second.getParameterList().getParametersCount();
        }
        return false;
      }
    });
  }
}
