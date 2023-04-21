// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LombokLibraryUtil;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.javadoc.PsiDocToken;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.util.ObjectUtils.tryCast;

public class LombokGetterMayBeUsedInspection extends AbstractBaseJavaLocalInspectionTool
  implements CleanupLocalInspectionTool {

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (!LombokLibraryUtil.hasLombokLibrary(holder.getProject())) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }

    return new LombokGetterMayBeUsedVisitor(holder, null);
  }

  private static class LombokGetterMayBeUsedVisitor extends JavaElementVisitor {
    private final @Nullable ProblemsHolder myHolder;

    private final @Nullable LombokGetterMayBeUsedInspection.LombokGetterMayBeUsedFix lombokGetterMayBeUsedFix;

    private LombokGetterMayBeUsedVisitor(
      @Nullable ProblemsHolder holder,
      @Nullable LombokGetterMayBeUsedInspection.LombokGetterMayBeUsedFix lombokGetterMayBeUsedFix
    ) {
      this.myHolder = holder;
      this.lombokGetterMayBeUsedFix = lombokGetterMayBeUsedFix;
    }

    @Override
    public void visitJavaFile(@NotNull PsiJavaFile psiJavaFile) {
    }

    @Override
    public void visitClass(@NotNull PsiClass psiClass) {
      List<PsiField> annotatedFields = new ArrayList<>();
      List<Pair<PsiField, PsiMethod>> instanceCandidates = new ArrayList<>();
      List<Pair<PsiField, PsiMethod>> staticCandidates = new ArrayList<>();
      for (PsiMethod method : psiClass.getMethods()) {
        processMethod(method, instanceCandidates, staticCandidates);
      }
      boolean isGetterAtClassLevel = true;
      for (PsiField field : psiClass.getFields()) {
        PsiAnnotation annotation = field.getAnnotation("lombok.Getter");
        if (annotation != null) {
          if (!annotation.getAttributes().isEmpty()) {
            isGetterAtClassLevel = false;
          } else {
            annotatedFields.add(field);
          }
          break;
        }
        if (!field.hasModifierProperty(PsiModifier.STATIC)) {
          boolean found = false;
          for (Pair<PsiField, PsiMethod> instanceCandidate : instanceCandidates) {
            if (field.equals(instanceCandidate.getFirst())) {
              found = true;
              break;
            }
          }
          isGetterAtClassLevel &= found;
        }
      }
      List<Pair<PsiField, PsiMethod>> allCandidates = new ArrayList<>(staticCandidates);
      if (isGetterAtClassLevel && (!instanceCandidates.isEmpty() || !annotatedFields.isEmpty()) ) {
        warnOrFix(psiClass, instanceCandidates, annotatedFields);
      } else {
        allCandidates.addAll(instanceCandidates);
      }
      for (Pair<PsiField, PsiMethod> candidate : allCandidates) {
        warnOrFix(candidate.getFirst(), candidate.getSecond());
      }
    }

    public void visitMethodForFix(@NotNull PsiMethod psiMethod) {
      List<Pair<PsiField, PsiMethod>> fieldsAndMethods = new ArrayList<>();
      if (!processMethod(psiMethod, fieldsAndMethods, fieldsAndMethods)) return;
      if (!fieldsAndMethods.isEmpty()) {
        warnOrFix(fieldsAndMethods.get(0).getFirst(), fieldsAndMethods.get(0).getSecond());
      }
    }

    private static boolean processMethod(
      @NotNull PsiMethod method,
      @NotNull List<Pair<PsiField, PsiMethod>> instanceCandidates,
      @NotNull List<Pair<PsiField, PsiMethod>> staticCandidates
    ) {
      final PsiType returnType = method.getReturnType();
      if (!method.hasModifierProperty(PsiModifier.PUBLIC)
          || method.isConstructor()
          || method.hasParameters()
          || method.getThrowsTypes().length != 0
          || method.hasModifierProperty(PsiModifier.FINAL)
          || method.hasModifierProperty(PsiModifier.ABSTRACT)
          || method.hasModifierProperty(PsiModifier.SYNCHRONIZED)
          || method.hasModifierProperty(PsiModifier.NATIVE)
          || method.hasModifierProperty(PsiModifier.STRICTFP)
          || method.getAnnotations().length != 0
          || PsiTypes.voidType().equals(returnType)
          || returnType == null
          || returnType.getAnnotations().length != 0
          || !method.isWritable()) {
        return false;
      }
      final boolean isMethodStatic = method.hasModifierProperty(PsiModifier.STATIC);
      final String methodName = method.getName();
      final boolean isBooleanType = PsiTypes.booleanType().equals(returnType);
      if ((isBooleanType ? !methodName.startsWith("is") : !methodName.startsWith("get"))
          || methodName.length() == 3
          || Character.isDigit(methodName.charAt(3))) {
        return false;
      }
      final String fieldName = isBooleanType
                               ? methodName.substring(2, 3).toLowerCase(Locale.ROOT) + methodName.substring(3)
                                 : methodName.substring(3, 4).toLowerCase(Locale.ROOT) + methodName.substring(4);
      if (method.getBody() == null) {
        return false;
      }
      final PsiStatement @NotNull [] methodStatements = method.getBody().getStatements();
      if (methodStatements.length != 1) {
        return false;
      }
      final PsiReturnStatement returnStatement = tryCast(methodStatements[0], PsiReturnStatement.class);
      if (returnStatement == null) {
        return false;
      }
      final PsiReferenceExpression fieldRef = tryCast(returnStatement.getReturnValue(), PsiReferenceExpression.class);
      if (fieldRef == null) {
        return false;
      }
      final @Nullable PsiExpression qualifier = fieldRef.getQualifierExpression();
      final @Nullable PsiThisExpression thisExpression = tryCast(qualifier, PsiThisExpression.class);
      final PsiClass psiClass = PsiTreeUtil.getParentOfType(method, PsiClass.class);
      if (psiClass == null) {
        return false;
      }
      if (qualifier != null) {
        if (thisExpression == null) {
          return false;
        } else if (thisExpression.getQualifier() != null) {
          if (!thisExpression.getQualifier().isReferenceTo(psiClass)) {
            return false;
          }
        }
      }
      final @Nullable String identifier = fieldRef.getReferenceName();
      if (identifier == null) {
        return false;
      }
      if (!identifier.equals(fieldName)
          && !identifier.equals(fieldName.substring(0, 1).toUpperCase(Locale.ROOT) + fieldName.substring(1))) {
        return false;
      }
      final PsiField field = psiClass.findFieldByName(identifier, false);
      if (field == null
          || !field.isWritable()
          || isMethodStatic != field.hasModifierProperty(PsiModifier.STATIC)
          || !field.getType().equals(returnType)) {
        return false;
      }
      if (isMethodStatic) {
        staticCandidates.add(Pair.pair(field, method));
      } else {
        instanceCandidates.add(Pair.pair(field, method));
      }
      return true;
    }

    private void warnOrFix(
      @NotNull PsiClass psiClass,
      @NotNull List<Pair<PsiField, PsiMethod>> fieldsAndMethods,
      @NotNull List<PsiField> annotatedFields
    ) {
      if (myHolder != null) {
        final LocalQuickFix fix = new LombokGetterMayBeUsedFix(Objects.requireNonNull(psiClass.getName()));
        myHolder.registerProblem(psiClass,
                                 InspectionGadgetsBundle.message("inspection.lombok.getter.may.be.used.display.class.message",
                                                                 psiClass.getName()), fix);
      } else if (lombokGetterMayBeUsedFix != null) {
        lombokGetterMayBeUsedFix.effectivelyDoFix(psiClass, fieldsAndMethods, annotatedFields);
      }
    }

    private void warnOrFix(@NotNull PsiField field, @NotNull PsiMethod method) {
      if (myHolder != null) {
        final LocalQuickFix fix = new LombokGetterMayBeUsedFix(field.getName());
        myHolder.registerProblem(method,
                                 InspectionGadgetsBundle.message("inspection.lombok.getter.may.be.used.display.field.message",
                                                                 field.getName()), fix);
      } else if (lombokGetterMayBeUsedFix != null) {
        lombokGetterMayBeUsedFix.effectivelyDoFix(field, method);
      }
    }
  }

  private static class LombokGetterMayBeUsedFix implements LocalQuickFix {
    private final @NotNull String myText;
    private Project project;

    private LombokGetterMayBeUsedFix(@NotNull String text) {
      myText = text;
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getName() {
      return InspectionGadgetsBundle.message("inspection.lombok.getter.may.be.used.display.fix.name", myText);
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("inspection.lombok.getter.may.be.used.display.fix.family.name");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      this.project = project;
      final PsiElement element = descriptor.getPsiElement();
      if (element instanceof PsiMethod) {
        new LombokGetterMayBeUsedVisitor(null, this).visitMethodForFix((PsiMethod)element);
      } else if (element instanceof PsiClass) {
        new LombokGetterMayBeUsedVisitor(null, this).visitClass((PsiClass)element);
      }
    }

    private void effectivelyDoFix(@NotNull PsiField field, @NotNull PsiMethod method) {
      final PsiModifierList modifierList = field.getModifierList();
      if (modifierList == null) {
        return;
      }
      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      final PsiAnnotation annotation = factory.createAnnotationFromText("@lombok.Getter", field);
      JavaCodeStyleManager.getInstance(project).shortenClassReferences(annotation);
      modifierList.addAfter(annotation, null);
      removeMethodAndMoveJavaDoc(field, method);
    }

    public void effectivelyDoFix(@NotNull PsiClass aClass, @NotNull List<Pair<PsiField, PsiMethod>> fieldsAndMethods,
                                 @NotNull List<PsiField> annotatedFields) {
      for (Pair<PsiField, PsiMethod> fieldAndMethod : fieldsAndMethods) {
        PsiField field = fieldAndMethod.getFirst();
        PsiMethod method = fieldAndMethod.getSecond();
        removeMethodAndMoveJavaDoc(field, method);
      }
      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      final PsiAnnotation newAnnotation = factory.createAnnotationFromText("@lombok.Getter", aClass);
      JavaCodeStyleManager.getInstance(project).shortenClassReferences(newAnnotation);
      final PsiModifierList modifierList = aClass.getModifierList();
      if (modifierList == null) {
        return;
      }
      modifierList.addAfter(newAnnotation, null);
      for (PsiField annotatedField : annotatedFields) {
        PsiAnnotation oldAnnotation = annotatedField.getAnnotation("lombok.Getter");
        if (oldAnnotation != null) {
          new CommentTracker().deleteAndRestoreComments(oldAnnotation);
        }
      }
    }

    private void removeMethodAndMoveJavaDoc(@NotNull PsiField field, @NotNull PsiMethod method) {
      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      CommentTracker tracker = new CommentTracker();
      PsiDocComment methodJavaDoc = method.getDocComment();
      if (methodJavaDoc != null) {
        tracker.text(methodJavaDoc);
        PsiDocComment fieldJavaDoc = field.getDocComment();
        List<String> methodJavaDocTokens = Arrays.stream(methodJavaDoc.getChildren())
          .filter(e -> e instanceof PsiDocToken)
          .map(PsiElement::getText)
          .filter(text -> !text.matches("\\s*\\*\\s*"))
          .toList();
        methodJavaDocTokens = methodJavaDocTokens.subList(1, methodJavaDocTokens.size() - 1);
        String javaDocGetterText = String.join("\n* ", methodJavaDocTokens);
        PsiDocTag[] returnTags = methodJavaDoc.findTagsByName("return");
        if (fieldJavaDoc == null) {
          if (javaDocGetterText.isEmpty()) {
            fieldJavaDoc = factory.createDocCommentFromText("/**\n*/");
          } else {
            fieldJavaDoc = factory.createDocCommentFromText("/**\n* -- GETTER --\n* " + javaDocGetterText + "\n*/");
          }
          for (PsiDocTag returnTag : returnTags) {
            fieldJavaDoc.add(returnTag);
          }
          field.getParent().addBefore(fieldJavaDoc, field);
        } else {
          @NotNull PsiElement @NotNull [] fieldJavaDocChildren = Arrays.stream(fieldJavaDoc.getChildren())
            .filter(e -> e instanceof PsiDocToken)
            .toArray(PsiElement[]::new);
          @NotNull PsiElement fieldJavaDocChild = fieldJavaDocChildren[fieldJavaDocChildren.length - 2];
          PsiDocComment newMethodJavaDoc = factory.createDocCommentFromText("/**\n* -- GETTER --\n* " + javaDocGetterText + "\n*/");
          PsiElement[] tokens = newMethodJavaDoc.getChildren();
          for (int i = tokens.length - 2; 0 < i; i--) {
            fieldJavaDoc.addAfter(tokens[i], fieldJavaDocChild);
          }
          for (PsiDocTag returnTag : returnTags) {
            fieldJavaDoc.add(returnTag);
          }
        }
        methodJavaDoc.delete();
      }
      tracker.delete(method);
      tracker.insertCommentsBefore(field);
    }
  }
}
