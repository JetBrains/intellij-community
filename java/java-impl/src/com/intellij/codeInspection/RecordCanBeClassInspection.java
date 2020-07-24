// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.codeInsight.AnnotationTargetUtil;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingFeature;
import com.intellij.codeInsight.intention.AddAnnotationPsiFix;
import com.intellij.codeInsight.intention.impl.ConvertCompactConstructorToCanonicalAction;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.PsiAnnotation.TargetType;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.util.JavaPsiRecordUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.siyeh.ig.psiutils.MethodUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Objects;

public class RecordCanBeClassInspection extends AbstractBaseJavaLocalInspectionTool {
  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (!HighlightingFeature.RECORDS.isAvailable(holder.getFile())) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    return new JavaElementVisitor() {
      @Override
      public void visitClass(PsiClass aClass) {
        if (aClass.isRecord()) {
          if (InspectionProjectProfileManager.isInformationLevel(getShortName(), aClass)) {
            PsiElement brace = aClass.getLBrace();
            if (brace != null) {
              holder.registerProblem(aClass, TextRange.create(0, brace.getStartOffsetInParent() + brace.getTextLength()),
                                     JavaBundle.message("inspection.message.record.can.be.converted.to.class"),
                                     new ConvertRecordToClassFix());
            }
            else {
              holder.registerProblem(aClass, JavaBundle.message("inspection.message.record.can.be.converted.to.class"),
                                     new ConvertRecordToClassFix());
            }
          }
          else {
            PsiIdentifier identifier = aClass.getNameIdentifier();
            if (identifier != null) {
              holder.registerProblem(identifier, JavaBundle.message("inspection.message.record.can.be.converted.to.class"),
                                     new ConvertRecordToClassFix());
            }
          }
        }
      }
    };
  }

  private static class ConvertRecordToClassFix implements LocalQuickFix {
    @Override
    public @IntentionFamilyName @NotNull String getFamilyName() {
      return JavaBundle.message("intention.family.name.convert.record.to.class");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiClass recordClass = PsiTreeUtil.getNonStrictParentOfType(descriptor.getStartElement(), PsiClass.class);
      if (recordClass == null || !recordClass.isRecord()) return;

      String recordClassText = generateText(recordClass);
      PsiJavaFile psiFile =
        (PsiJavaFile)PsiFileFactory.getInstance(project).createFileFromText("Dummy.java", JavaFileType.INSTANCE, recordClassText);
      PsiClass converted = psiFile.getClasses()[0];
      postProcessAnnotations(recordClass, converted);
      JavaCodeStyleManager.getInstance(project).shortenClassReferences(recordClass.replace(converted));
    }

    @NotNull
    private static String generateText(@NotNull PsiClass recordClass) {
      PsiRecordComponent[] components = recordClass.getRecordComponents();
      PsiField lastField =
        StreamEx.of(recordClass.getFields()).filter(field -> field.hasModifierProperty(PsiModifier.STATIC)).reduce((a, b) -> b)
          .orElse(null);
      PsiMethod lastMethod =
        StreamEx.of(recordClass.getMethods()).filter(method -> !(method instanceof SyntheticElement)).reduce((a, b) -> b).orElse(null);
      PsiElement lBrace = recordClass.getLBrace();
      PsiElement insertFieldsAfter = lastField == null ? lBrace : lastField;
      PsiElement insertMethodsAfter = lastMethod == null ? insertFieldsAfter : lastMethod;
      StringBuilder result = new StringBuilder();
      for (PsiElement child = recordClass.getFirstChild(); child != null; child = child.getNextSibling()) {
        result.append(getChildText(recordClass, child));
        if (child == insertFieldsAfter) {
          insertFields(result, components);
          insertConstructor(result, JavaPsiRecordUtil.findCanonicalConstructor(recordClass), recordClass);
        }
        if (child == insertMethodsAfter) {
          insertMethods(result, recordClass, recordClass.getRecordComponents());
        }
      }
      if (insertFieldsAfter == null) {
        result.append("{\n");
        insertFields(result, components);
        insertConstructor(result, JavaPsiRecordUtil.findCanonicalConstructor(recordClass), recordClass);
        insertMethods(result, recordClass, recordClass.getRecordComponents());
        result.append("}");
      }
      return result.toString();
    }

    private static String getChildText(PsiClass recordClass, PsiElement child) {
      if (child instanceof PsiKeyword && ((PsiKeyword)child).getTokenType().equals(JavaTokenType.RECORD_KEYWORD)) {
        PsiModifierList modifierList = Objects.requireNonNull(recordClass.getModifierList());
        String text = "class";
        if (!modifierList.hasExplicitModifier(PsiModifier.FINAL)) {
          text = "final " + text;
        }
        if (recordClass.getContainingClass() != null && !modifierList.hasExplicitModifier(PsiModifier.STATIC)) {
          text = "static " + text;
        }
        return text;
      }
      if (child instanceof PsiRecordHeader) {
        return "";
      }
      if (child instanceof PsiDocComment) {
        PsiDocComment copy = (PsiDocComment)child.copy();
        for (PsiDocTag param : copy.findTagsByName("param")) {
          param.delete();
        }
        return copy.getText();
      }
      if (child instanceof PsiMethod && JavaPsiRecordUtil.isCompactConstructor((PsiMethod)child)) {
        return ConvertCompactConstructorToCanonicalAction.generateCanonicalConstructor((PsiMethod)child).getText();
      }
      return child.getText();
    }

    private static void postProcessAnnotations(PsiClass recordClass, PsiClass converted) {
      PsiRecordComponent[] components = recordClass.getRecordComponents();
      Map<String, PsiRecordComponent> componentMap = StreamEx.of(components).toMap(c -> c.getName(), c -> c, (a, b) -> a);
      for (PsiField field : converted.getFields()) {
        postProcessFieldAnnotations(componentMap, field);
      }
      for (PsiMethod method : converted.getMethods()) {
        postProcessMethodAnnotations(recordClass, componentMap, method);
      }
    }

    private static void postProcessFieldAnnotations(Map<String, PsiRecordComponent> componentMap, PsiField field) {
      if (field.hasModifierProperty(PsiModifier.STATIC)) return;
      PsiRecordComponent component = componentMap.get(field.getName());
      if (component == null) return;
      copyAnnotations(field, component, TargetType.FIELD);
    }

    private static void postProcessMethodAnnotations(PsiClass recordClass, Map<String, PsiRecordComponent> componentMap, PsiMethod method) {
      if (method.hasModifierProperty(PsiModifier.STATIC)) return;
      if (method.isConstructor()) {
        PsiMethod recordCtor = recordClass.findMethodBySignature(method, false);
        // Record constructor is generated with all component annotations, so we need to delete inappropriate ones
        if (recordCtor instanceof SyntheticElement) {
          for (PsiParameter parameter : method.getParameterList().getParameters()) {
            for (PsiAnnotation annotation : parameter.getAnnotations()) {
              if (AnnotationTargetUtil.findAnnotationTarget(annotation, TargetType.PARAMETER, TargetType.TYPE_USE) == null) {
                annotation.delete();
              }
            }
          }
        }
      }
      else if (method.getParameterList().isEmpty()) {
        PsiRecordComponent component = componentMap.get(method.getName());
        if (component != null) {
          PsiMethod recordMethod = recordClass.findMethodBySignature(method, false);
          if (recordMethod == null || recordMethod instanceof SyntheticElement) {
            copyAnnotations(method, component, TargetType.METHOD);
          }
          if (method.findSuperMethods().length > 0) {
            AddAnnotationPsiFix
              .addPhysicalAnnotationIfAbsent(CommonClassNames.JAVA_LANG_OVERRIDE, PsiNameValuePair.EMPTY_ARRAY, method.getModifierList());
          }
          else {
            PsiAnnotation override = method.getModifierList().findAnnotation(CommonClassNames.JAVA_LANG_OVERRIDE);
            if (override != null) {
              override.delete();
            }
          }
        }
      }
    }

    private static void copyAnnotations(PsiModifierListOwner modifierListOwner,
                                        PsiRecordComponent component, TargetType targetType) {
      for (PsiAnnotation annotation : component.getAnnotations()) {
        String qualifiedName = annotation.getQualifiedName();
        if (qualifiedName == null || AnnotationTargetUtil.findAnnotationTarget(annotation, TargetType.TYPE_USE) == null) continue;
        PsiAnnotationOwner target = AnnotationTargetUtil.getTarget(modifierListOwner, qualifiedName);
        if (target == null) continue;
        PsiAnnotation dummy = AddAnnotationPsiFix.addPhysicalAnnotationIfAbsent("Dummy", PsiNameValuePair.EMPTY_ARRAY, target);
        if (dummy != null) {
          dummy.replace(annotation);
        }
      }
      // Non-type-use annotations are added at the beginning, so we try to preserve order iterating original annotations backwards
      for (PsiAnnotation annotation : StreamEx.ofReversed(component.getAnnotations())) {
        String qualifiedName = annotation.getQualifiedName();
        if (qualifiedName == null || AnnotationTargetUtil.findAnnotationTarget(annotation, targetType) == null) continue;
        PsiAnnotationOwner target = AnnotationTargetUtil.getTarget(modifierListOwner, qualifiedName);
        if (target == null) continue;
        PsiAnnotation dummy = AddAnnotationPsiFix.addPhysicalAnnotationIfAbsent("Dummy", PsiNameValuePair.EMPTY_ARRAY, target);
        if (dummy != null) {
          dummy.replace(annotation);
        }
      }
    }

    private static void insertConstructor(StringBuilder result, PsiMethod canonicalConstructor, PsiClass psiClass) {
      if (canonicalConstructor instanceof SyntheticElement) {
        PsiDocComment docComment = psiClass.getDocComment();
        if (docComment != null) {
          PsiDocTag[] params = docComment.findTagsByName("param");
          PsiDocComment ctorComment = JavaPsiFacade.getElementFactory(psiClass.getProject()).createDocCommentFromText("/**\n*/");
          for (PsiDocTag param : params) {
            ctorComment.add(param);
          }
          result.append(ctorComment.getText());
        }
        result.append(canonicalConstructor.getText());
      }
    }

    private static void insertMethods(StringBuilder result, PsiClass psiClass, PsiRecordComponent @NotNull [] components) {
      boolean hasEquals = false, hasHashCode = false, hasToString = false;
      for (PsiMethod method : psiClass.getMethods()) {
        if (!method.isPhysical() && JavaPsiRecordUtil.getRecordComponentForAccessor(method) != null) {
          result.append(method.getText()).append("\n");
        }
        hasEquals |= MethodUtils.isEquals(method);
        hasHashCode |= MethodUtils.isHashCode(method);
        hasToString |= MethodUtils.isToString(method);
      }
      if (!hasEquals) {
        result.append(generateEquals(psiClass, components));
      }
      if (!hasHashCode) {
        result.append(generateHashCode(components));
      }
      if (!hasToString) {
        result.append(generateToString(psiClass, components));
      }
    }

    private static @NotNull String generateToString(PsiClass psiClass, PsiRecordComponent @NotNull [] components) {
      String toStringExpression = components.length == 0 ? '"' + psiClass.getName() + "[]\"" :
                                  '"' +
                                  psiClass.getName() +
                                  "[\"+\n" +
                                  StreamEx.of(components).map(c -> '"' + c.getName() + "=\"+" + c.getName()).joining("+\", \"+\n") +
                                  "+']'";
      return "@" + CommonClassNames.JAVA_LANG_OVERRIDE + "\n" +
             "public String toString() {\n" +
             "return " + toStringExpression + ";\n" +
             "}\n";
    }

    private static @NotNull String generateHashCode(PsiRecordComponent @NotNull [] components) {
      String hashExpression = components.length == 0 ? "1" : CommonClassNames.JAVA_UTIL_OBJECTS + ".hash(" +
                                                             StreamEx.of(components).map(c -> c.getName()).joining(",") + ")";
      return "@" + CommonClassNames.JAVA_LANG_OVERRIDE + "\n" +
             "public int hashCode() {\n" +
             "return " + hashExpression + ";\n" +
             "}\n";
    }

    private static @NotNull String generateEquals(PsiClass psiClass, PsiRecordComponent @NotNull [] components) {
      String equalsExpression = StreamEx.of(components).map(c -> generateEqualsExpression(c)).joining(" &&\n");
      String body = components.length == 0
                    ?
                    "return obj == this || obj != null && obj.getClass() == this.getClass();"
                    :
                    "if(obj == this) return true;\n" +
                    "if(obj == null || obj.getClass() != this.getClass()) return false;\n" +
                    "var that = (" + psiClass.getName() + ")obj;\n" +
                    "return " + equalsExpression + ";\n";
      return "@" + CommonClassNames.JAVA_LANG_OVERRIDE + "\n" +
             "public boolean equals(" + CommonClassNames.JAVA_LANG_OBJECT + " obj) {\n" +
             body +
             "}\n";
    }

    private static @NotNull String generateEqualsExpression(PsiRecordComponent component) {
      PsiType type = component.getType();
      if (type instanceof PsiPrimitiveType) {
        if (TypeConversionUtil.isIntegralNumberType(type) || type.equals(PsiType.BOOLEAN)) {
          return "this." + component.getName() + "==that." + component.getName();
        }
        if (TypeConversionUtil.isFloatOrDoubleType(type)) {
          String method = type.equalsToText("float") ? "Float.floatToIntBits" : "Double.doubleToLongBits";
          return method + "(this." + component.getName() + ")==" + method + "(that." + component.getName() + ")";
        }
      }
      return CommonClassNames.JAVA_UTIL_OBJECTS + ".equals(this." + component.getName() + ",that." + component.getName() + ")";
    }

    private static void insertFields(StringBuilder result, PsiRecordComponent[] components) {
      for (PsiRecordComponent component : components) {
        PsiField field = JavaPsiRecordUtil.getFieldForComponent(component);
        if (field != null) {
          result.append(field.getText()).append("\n");
        }
      }
    }
  }
}
