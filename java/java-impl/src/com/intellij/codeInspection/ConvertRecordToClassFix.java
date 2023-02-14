// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.codeInsight.AnnotationTargetUtil;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingFeature;
import com.intellij.codeInsight.intention.AddAnnotationPsiFix;
import com.intellij.codeInsight.intention.impl.ConvertCompactConstructorToCanonicalAction;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.java.JavaBundle;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.lang.java.parser.DeclarationParser;
import com.intellij.lang.java.parser.JavaParser;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.source.DummyHolder;
import com.intellij.psi.impl.source.DummyHolderFactory;
import com.intellij.psi.impl.source.JavaDummyElement;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.util.JavaPsiRecordUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.fixes.AddSerialVersionUIDFix;
import com.siyeh.ig.psiutils.MethodUtils;
import com.siyeh.ig.psiutils.SerializationUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;

import static com.intellij.psi.CommonClassNames.SERIAL_VERSION_UID_FIELD_NAME;

public class ConvertRecordToClassFix extends LocalQuickFixAndIntentionActionOnPsiElement {
  private final LanguageLevel myLanguageLevel;

  public ConvertRecordToClassFix(@NotNull PsiElement candidate) {
    super(candidate);
    myLanguageLevel = PsiUtil.getLanguageLevel(candidate);
  }

  @Override
  public @IntentionFamilyName @NotNull String getFamilyName() {
    return JavaBundle.message("intention.family.name.convert.record.to.class");
  }

  @Override
  public @IntentionName @NotNull String getText() {
    return getFamilyName();
  }

  @Nullable
  public static PsiClass tryMakeRecord(@NotNull PsiElement element) {
    // We use java.util.Objects for code generation, but it's absent before Java 7
    if (!PsiUtil.isLanguageLevel7OrHigher(element)) return null;
    PsiJavaFile maybeRecord = (PsiJavaFile)PsiFileFactory.getInstance(element.getProject())
      .createFileFromText("Dummy.java", JavaLanguage.INSTANCE, element.getText(), false, false);
    PsiUtil.FILE_LANGUAGE_LEVEL_KEY.set(maybeRecord, LanguageLevel.JDK_16);
    PsiClass[] classes = maybeRecord.getClasses();
    if (classes.length == 1 && classes[0].isRecord()) {
      return classes[0];
    }
    return null;
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile file,
                     @Nullable Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    PsiClass recordClass;
    if (startElement instanceof PsiErrorElement) {
      recordClass = tryMakeRecord(startElement);
    } else {
      recordClass = ObjectUtils.tryCast(startElement, PsiClass.class);
    }
    if (recordClass == null || !recordClass.isRecord()) return;

    String recordClassText = generateText(recordClass);
    JavaDummyElement dummyElement = new JavaDummyElement(
      recordClassText, builder -> JavaParser.INSTANCE.getDeclarationParser().parse(builder, DeclarationParser.Context.CLASS),
      LanguageLevel.JDK_16);
    DummyHolder holder = DummyHolderFactory.createHolder(file.getManager(), dummyElement, recordClass);
    PsiClass converted = (PsiClass)Objects.requireNonNull(SourceTreeToPsiMap.treeElementToPsi(holder.getTreeElement().getFirstChildNode()));
    postProcessAnnotations(recordClass, converted);
    PsiClass result = replace(project, file, startElement, converted);
    CodeStyleManager.getInstance(project).reformat(JavaCodeStyleManager.getInstance(project).shortenClassReferences(result));
  }

  private static @NotNull PsiClass replace(@NotNull Project project,
                                           @NotNull PsiFile file,
                                           @NotNull PsiElement startElement,
                                           @NotNull PsiClass converted) {
    if (startElement instanceof PsiErrorElement) {
      // Older Java version: try to extract part of code which looks like a record
      TextRange range = startElement.getTextRange();
      Document document = file.getViewProvider().getDocument();
      if (document != null) {
        document.replaceString(range.getStartOffset(), range.getEndOffset(), converted.getText());
        PsiDocumentManager.getInstance(project).commitDocument(document);
        PsiClass pastedClass = PsiTreeUtil.getParentOfType(file.findElementAt(range.getStartOffset()), PsiClass.class);
        if (pastedClass != null) {
          return pastedClass;
        }
      }
    }
    return (PsiClass)startElement.replace(converted);
  }

  @NotNull
  private String generateText(@NotNull PsiClass recordClass) {
    PsiField lastField =
      StreamEx.ofReversed(recordClass.getFields()).findFirst(field -> field.hasModifierProperty(PsiModifier.STATIC)).orElse(null);
    PsiMethod lastMethod =
      StreamEx.ofReversed(recordClass.getMethods()).findFirst(method -> !(method instanceof SyntheticElement)).orElse(null);
    PsiElement lBrace = recordClass.getLBrace();
    PsiElement insertFieldsAfter = lastField == null ? lBrace : lastField;
    PsiElement insertMethodsAfter = lastMethod == null ? insertFieldsAfter : lastMethod;
    StringBuilder result = new StringBuilder();
    for (PsiElement child = recordClass.getFirstChild(); child != null; child = child.getNextSibling()) {
      result.append(getChildText(recordClass, child));
      if (child == insertFieldsAfter) {
        insertFields(result, recordClass);
        insertConstructor(result, JavaPsiRecordUtil.findCanonicalConstructor(recordClass), recordClass);
      }
      if (child == insertMethodsAfter) {
        insertMethods(result, recordClass, recordClass.getRecordComponents());
      }
    }
    if (insertFieldsAfter == null) {
      result.append("{\n");
      insertFields(result, recordClass);
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
    boolean serializableRecord = SerializationUtils.isSerializable(recordClass);
    for (PsiField field : converted.getFields()) {
      if (serializableRecord && SerializationUtils.isSerialVersionUid(field)) {
        postProcessSerialVersionUIDFieldAnnotations(recordClass, field);
      }
      else {
        postProcessFieldAnnotations(componentMap, field);
      }
    }
    for (PsiMethod method : converted.getMethods()) {
      postProcessMethodAnnotations(recordClass, componentMap, method);
    }
  }

  private static void postProcessSerialVersionUIDFieldAnnotations(PsiClass recordClass, PsiField convertedField) {
    // converted field may have wrong annotation fqn, so we have to find the origin field at first
    PsiField originSerialVersionUIDField = getSerialVersionUIDField(recordClass);
    if (originSerialVersionUIDField == null || !originSerialVersionUIDField.hasAnnotation(CommonClassNames.JAVA_IO_SERIAL)) {
      AddSerialVersionUIDFix.annotateFieldWithSerial(convertedField);
    }
  }

  private static void postProcessFieldAnnotations(Map<String, PsiRecordComponent> componentMap, PsiField field) {
    if (field.hasModifierProperty(PsiModifier.STATIC)) return;
    PsiRecordComponent component = componentMap.get(field.getName());
    if (component == null) return;
    copyAnnotations(field, component, PsiAnnotation.TargetType.FIELD);
  }

  private static void postProcessMethodAnnotations(PsiClass recordClass, Map<String, PsiRecordComponent> componentMap, PsiMethod method) {
    if (method.hasModifierProperty(PsiModifier.STATIC)) return;
    if (method.isConstructor()) {
      PsiMethod recordCtor = recordClass.findMethodBySignature(method, false);
      // Record constructor is generated with all component annotations, so we need to delete inappropriate ones
      if (recordCtor instanceof SyntheticElement) {
        for (PsiParameter parameter : method.getParameterList().getParameters()) {
          for (PsiAnnotation annotation : parameter.getAnnotations()) {
            if (AnnotationTargetUtil
                  .findAnnotationTarget(annotation, PsiAnnotation.TargetType.PARAMETER, PsiAnnotation.TargetType.TYPE_USE) == null) {
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
          copyAnnotations(method, component, PsiAnnotation.TargetType.METHOD);
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
                                      PsiRecordComponent component, PsiAnnotation.TargetType targetType) {
    for (PsiAnnotation annotation : component.getAnnotations()) {
      String qualifiedName = annotation.getQualifiedName();
      if (qualifiedName == null || AnnotationTargetUtil.findAnnotationTarget(annotation, PsiAnnotation.TargetType.TYPE_USE) == null) {
        continue;
      }
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
      if (qualifiedName == null || AnnotationTargetUtil.findAnnotationTarget(annotation, targetType) == null ||
          AnnotationTargetUtil.findAnnotationTarget(annotation, PsiAnnotation.TargetType.TYPE_USE) != null) {
        continue;
      }
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

  private void insertMethods(StringBuilder result, PsiClass psiClass, PsiRecordComponent @NotNull [] components) {
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

  private @NotNull String generateEquals(PsiClass psiClass, PsiRecordComponent @NotNull [] components) {
    String equalsExpression = StreamEx.of(components).map(c -> generateEqualsExpression(c)).joining(" &&\n");
    String body = components.length == 0
                  ?
                  "return obj == this || obj != null && obj.getClass() == this.getClass();"
                  :
                  "if(obj == this) return true;\n" +
                  "if(obj == null || obj.getClass() != this.getClass()) return false;\n" +
                  (myLanguageLevel.isAtLeast(HighlightingFeature.LVTI.getLevel()) ? PsiKeyword.VAR : psiClass.getName()) +
                   " that = (" + psiClass.getName() + ")obj;\n" +
                   "return " + equalsExpression + ";\n";
    return "@" + CommonClassNames.JAVA_LANG_OVERRIDE + "\n" +
           "public boolean equals(" + CommonClassNames.JAVA_LANG_OBJECT + " obj) {\n" +
           body +
           "}\n";
  }

  private static @NotNull String generateEqualsExpression(PsiRecordComponent component) {
    PsiType type = component.getType();
    if (type instanceof PsiPrimitiveType) {
      if (TypeConversionUtil.isIntegralNumberType(type) || type.equals(PsiTypes.booleanType())) {
        return "this." + component.getName() + "==that." + component.getName();
      }
      if (TypeConversionUtil.isFloatOrDoubleType(type)) {
        String method = type.equalsToText("float") ? "Float.floatToIntBits" : "Double.doubleToLongBits";
        return method + "(this." + component.getName() + ")==" + method + "(that." + component.getName() + ")";
      }
    }
    return CommonClassNames.JAVA_UTIL_OBJECTS + ".equals(this." + component.getName() + ",that." + component.getName() + ")";
  }

  private static void insertFields(StringBuilder result, PsiClass recordClass) {
    if (SerializationUtils.isSerializable(recordClass)) {
      if (getSerialVersionUIDField(recordClass) == null) {
        result.append(AddSerialVersionUIDFix.generateSerialVersionUIDFieldText(0)).append("\n");
      }
    }
    for (PsiRecordComponent component : recordClass.getRecordComponents()) {
      PsiField field = JavaPsiRecordUtil.getFieldForComponent(component);
      if (field != null) {
        result.append(field.getText()).append("\n");
      }
    }
  }

  @Nullable
  private static PsiField getSerialVersionUIDField(@NotNull PsiClass recordClass) {
    return recordClass.findFieldByName(SERIAL_VERSION_UID_FIELD_NAME, false);
  }
}
