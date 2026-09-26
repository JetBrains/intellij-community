// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.javadoc;

import com.intellij.codeInsight.AnnotationTargetUtil;
import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.ide.highlighter.JavaHighlightingColors;
import com.intellij.lang.Language;
import com.intellij.lang.documentation.DocumentationSettings;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Predicates;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiAnnotationOwner;
import com.intellij.psi.PsiArrayInitializerMemberValue;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaModule;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiNameValuePair;
import com.intellij.psi.PsiQualifiedReferenceElement;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.PsiVariable;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.intellij.lang.annotations.Flow;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;


public final class AnnotationDocGenerator {
  private static final Logger LOG = Logger.getInstance(AnnotationDocGenerator.class);
  private final @NotNull PsiAnnotation myAnnotation;
  private final @NotNull PsiJavaCodeReferenceElement myNameReference;
  private final @NotNull PsiElement myContext;
  private final @Nullable PsiClass myTargetClass;
  private final boolean myResolveNotPossible;

  private AnnotationDocGenerator(@NotNull PsiAnnotation annotation,
                                 @NotNull PsiJavaCodeReferenceElement nameReference,
                                 @NotNull PsiElement context) {
    myAnnotation = annotation;
    myNameReference = nameReference;
    myContext = context;

    boolean indexNotReady = false;
    PsiElement target = null;
    try {
      target = nameReference.resolve();
    }
    catch (IndexNotReadyException e) {
      LOG.debug(e);
      indexNotReady = true;
    }
    myTargetClass = ObjectUtils.tryCast(target, PsiClass.class);
    myResolveNotPossible = indexNotReady;
  }

  boolean isNonDocumentedAnnotation() {
    return myTargetClass != null
           ? !JavaDocInfoGenerator.isDocumentedAnnotationType(myTargetClass)
           : isKnownNonDocumented(myAnnotation.getQualifiedName());
  }

  private static boolean isKnownNonDocumented(String annoQName) {
    return Flow.class.getName().equals(annoQName);
  }

  boolean isExternal() {
    return AnnotationUtil.isExternalAnnotation(myAnnotation);
  }

  public String getAnnotationQualifiedName() {
    return myAnnotation.getQualifiedName();
  }

  boolean isInferredTypeUseAnnotation() {
    return isInferred() && AnnotationTargetUtil.isTypeAnnotation(myAnnotation);
  }

  public boolean isInferred() {
    return AnnotationUtil.isInferredAnnotation(myAnnotation);
  }

  private static void appendStyledSpan(
    JavaDocInfoPrinter printer,
    boolean doSyntaxHighlighting,
    boolean isForRenderedDoc,
    @NotNull StringBuilder buffer,
    @NotNull TextAttributesKey attributesKey,
    @Nullable String value
  ) {
    var manager = EditorColorsManager.getInstance();
    TextAttributes attributes = manager != null
                                ? manager.getGlobalScheme().getAttributes(attributesKey)
                                : new TextAttributes();

    printer.printStylizedText(buffer, doSyntaxHighlighting, attributes, value,
                              DocumentationSettings.getHighlightingSaturation(isForRenderedDoc));
  }

  private static void appendHighlightedByLexerAndEncodedAsHtmlCodeSnippet(
    JavaDocInfoPrinter printer,
    boolean doSyntaxHighlighting,
    boolean isForRenderedDoc,
    @NotNull StringBuilder buffer,
    @NotNull Project project,
    @NotNull Language language,
    @Nullable String codeSnippet
  ) {
    printer.printHighlightedText(buffer, doSyntaxHighlighting, project, language, codeSnippet,
                                 DocumentationSettings.getHighlightingSaturation(isForRenderedDoc));
  }

  void generateAnnotation(
    StringBuilder buffer,
    AnnotationFormat format,
    boolean generateLink,
    boolean isForRenderedDoc,
    boolean doSyntaxHighlighting,
    JavaDocInfoPrinter printer) {
    String qualifiedName = myAnnotation.getQualifiedName();
    PsiClassType type = myTargetClass != null && qualifiedName != null &&
                        JavaDocUtil.findReferenceTarget(myContext.getManager(), qualifiedName, myContext) != null
                        ? JavaPsiFacade.getElementFactory(myContext.getProject()).createType(myTargetClass, PsiSubstitutor.EMPTY)
                        : null;

    boolean isInferred = isInferred();
    boolean red = type == null && !myResolveNotPossible && !isInferred && !isExternal();

    boolean isNonCodeAnnotation = isInferred || isExternal();
    boolean highlightNonCodeAnnotations = format == AnnotationFormat.ToolTip && isNonCodeAnnotation;

    boolean forceShortNames = format != AnnotationFormat.JavaDocComplete;
    StringBuilder annotationBuffer = new StringBuilder();

    if (red) {
      annotationBuffer.append('@');
    }
    else {
      appendStyledSpan(printer, doSyntaxHighlighting, isForRenderedDoc, annotationBuffer,
                       JavaHighlightingColors.ANNOTATION_NAME_ATTRIBUTES, "@");
    }
    String name = forceShortNames ? myNameReference.getReferenceName() : myNameReference.getText();
    if (type != null && generateLink) {
      StringBuilder styledNameBuilder = new StringBuilder();
      appendStyledSpan(printer, doSyntaxHighlighting, isForRenderedDoc, styledNameBuilder,
                       JavaHighlightingColors.ANNOTATION_NAME_ATTRIBUTES, name);
      String styledName = styledNameBuilder.toString();
      printer.printLink(annotationBuffer, myTargetClass, styledName, format == AnnotationFormat.JavaDocComplete);
    }
    else if (name != null) {
      appendStyledSpan(printer, doSyntaxHighlighting, isForRenderedDoc, annotationBuffer,
                       JavaHighlightingColors.ANNOTATION_NAME_ATTRIBUTES, name);
    }

    generateAnnotationAttributes(annotationBuffer, generateLink, isForRenderedDoc, doSyntaxHighlighting, printer);
    String annotation = annotationBuffer.toString();
    if (isInferred) {
      annotation = printer.printItalicText(new StringBuilder(), annotation).toString();
    }
    if (highlightNonCodeAnnotations) {
      annotation = printer.printBoldText(new StringBuilder(), annotation).toString();
    }
    if (red) {
      annotation = printer.printUnresolvedLink(new StringBuilder(), annotation).toString();
    }
    buffer.append(annotation);
    if (generateLink && isNonCodeAnnotation && !isForRenderedDoc && format != AnnotationFormat.ToolTip) {
      printer.printAnnotationHint(buffer, isInferred);
    }
  }

  private void generateAnnotationAttributes(
    StringBuilder buffer,
    boolean generateLink,
    boolean isForRenderedDoc,
    boolean doSyntaxHighlighting,
    JavaDocInfoPrinter printer
  ) {
    final PsiNameValuePair[] attributes = myAnnotation.getParameterList().getAttributes();
    if (attributes.length > 0) {
      appendStyledSpan(printer, doSyntaxHighlighting, isForRenderedDoc, buffer, JavaHighlightingColors.PARENTHESES, "(");
      boolean first = true;
      for (PsiNameValuePair pair : attributes) {
        if (!first) {
          appendStyledSpan(printer, doSyntaxHighlighting, isForRenderedDoc, buffer, JavaHighlightingColors.COMMA,
                           "," + printer.getEscapableChar(' '));
        }
        first = false;
        generateAnnotationAttribute(buffer, generateLink, pair, isForRenderedDoc, doSyntaxHighlighting, printer);
      }
      appendStyledSpan(printer, doSyntaxHighlighting, isForRenderedDoc, buffer, JavaHighlightingColors.PARENTHESES, ")");
    }
  }

  private static void generateAnnotationAttribute(
    StringBuilder buffer,
    boolean generateLink,
    PsiNameValuePair pair,
    boolean isForRenderedDoc,
    boolean doSyntaxHighlighting,
    JavaDocInfoPrinter printer
  ) {
    final String name = pair.getName();
    if (name != null) {
      appendStyledSpan(printer, doSyntaxHighlighting, isForRenderedDoc, buffer,
                       JavaHighlightingColors.ANNOTATION_ATTRIBUTE_NAME_ATTRIBUTES, name);
      appendStyledSpan(printer, doSyntaxHighlighting, isForRenderedDoc, buffer, JavaHighlightingColors.OPERATION_SIGN, " = ");
    }
    final PsiAnnotationMemberValue value = pair.getValue();
    if (value != null) {
      if (value instanceof PsiArrayInitializerMemberValue) {
        appendStyledSpan(printer, doSyntaxHighlighting, isForRenderedDoc, buffer, JavaHighlightingColors.BRACES, "{");
        boolean firstMember = true;
        for (PsiAnnotationMemberValue memberValue : ((PsiArrayInitializerMemberValue)value).getInitializers()) {
          if (!firstMember) {
            appendStyledSpan(printer, doSyntaxHighlighting, isForRenderedDoc, buffer, JavaHighlightingColors.COMMA, ",");
          }
          firstMember = false;
          appendLinkOrText(buffer, memberValue, generateLink, isForRenderedDoc, doSyntaxHighlighting, printer);
        }
        appendStyledSpan(printer, doSyntaxHighlighting, isForRenderedDoc, buffer, JavaHighlightingColors.BRACES, "}");
      }
      else {
        appendLinkOrText(buffer, value, generateLink, isForRenderedDoc, doSyntaxHighlighting, printer);
      }
    }
  }

  private static void appendLinkOrText(
    StringBuilder buffer,
    PsiAnnotationMemberValue memberValue,
    boolean generateLink,
    boolean isForRenderedDoc,
    boolean doSyntaxHighlighting,
    JavaDocInfoPrinter printer
  ) {
    if (memberValue instanceof PsiQualifiedReferenceElement) {
      String text = ((PsiQualifiedReferenceElement)memberValue).getCanonicalText();
      PsiElement resolve = null;
      try {
        resolve = ((PsiQualifiedReferenceElement)memberValue).resolve();
      }
      catch (Exception e) {
        LOG.debug(e);
      }

      if (resolve instanceof PsiField field) {
        PsiClass aClass = field.getContainingClass();

        if (generateLink) {
          int startOfPropertyNamePosition = text.lastIndexOf('.');
          if (startOfPropertyNamePosition != -1) {
            text = text.substring(0, startOfPropertyNamePosition) + '#' + text.substring(startOfPropertyNamePosition + 1);
          }
          else {
            if (aClass != null) text = aClass.getQualifiedName() + '#' + field.getName();
          }
          JavaDocInfoGeneratorFactory.getBuilder(field.getProject())
            .setIsGenerationForRenderedDoc(isForRenderedDoc)
            .setDoHighlightSignatures(doSyntaxHighlighting)
            .setPrinter(printer)
            .create()
            .generateLink(buffer, text, aClass != null ? aClass.getName() + '.' + field.getName() : null, memberValue, false);
        }
        else {
          appendHighlightedByLexerAndEncodedAsHtmlCodeSnippet(
            printer,
            doSyntaxHighlighting,
            isForRenderedDoc,
            buffer,
            memberValue.getProject(),
            memberValue.getLanguage(),
            aClass != null ? aClass.getName() + '.' + field.getName() : memberValue.getText());
        }
        return;
      }
    }

    appendHighlightedByLexerAndEncodedAsHtmlCodeSnippet(
      printer,
      doSyntaxHighlighting,
      isForRenderedDoc,
      buffer,
      memberValue.getProject(),
      memberValue.getLanguage(),
      memberValue.getText());
  }

  public static List<AnnotationDocGenerator> getAnnotationsToShow(@NotNull PsiAnnotationOwner owner, @NotNull PsiElement context) {
    if (owner instanceof PsiTypeParameter typeParameter) {
      return getAnnotationsToShow(typeParameter);
    }
    if (owner instanceof PsiModifierList modifierList) {
      return getAnnotationsToShow(((PsiModifierListOwner)modifierList.getParent()));
    }
    Set<String> shownAnnotations = new HashSet<>();
    List<AnnotationDocGenerator> generators = ContainerUtil.mapNotNull(
      owner.getAnnotations(), annotation -> forAnnotation(context, shownAnnotations, annotation));
    if (owner instanceof PsiArrayType type) {
      PsiType contextType = getContextType(context);
      if (type.equals(contextType)) {
        return StreamEx.of(getAnnotationsToShow((PsiModifierListOwner)context)).filter(anno -> anno.isInferredTypeUseAnnotation())
          .append(generators).toList();
      }
    }
    return generators;
  }

  static @Nullable PsiType getContextType(@NotNull PsiElement context) {
    return context instanceof PsiVariable var ? var.getType() :
           context instanceof PsiMethod method ? method.getReturnType() : null;
  }

  public static List<AnnotationDocGenerator> getAnnotationsToShow(@NotNull PsiModifierListOwner owner) {
    Set<String> shownAnnotations = new HashSet<>();
    return StreamEx.of(AnnotationUtil.getAllAnnotations(owner, false, null))
      .filter(owner instanceof PsiClass || owner instanceof PsiJavaModule ? Predicates.alwaysTrue()
                                                                          : anno -> !AnnotationTargetUtil.isTypeAnnotation(anno) ||
                                                                                    AnnotationUtil.isInferredAnnotation(anno))
      .map(annotation -> forAnnotation(owner, shownAnnotations, annotation))
      .nonNull()
      .toList();
  }

  static @Nullable AnnotationDocGenerator forAnnotation(@NotNull PsiElement context,
                                                        @NotNull Set<String> shownAnnotations,
                                                        @NotNull PsiAnnotation annotation) {
    PsiJavaCodeReferenceElement nameReferenceElement = annotation.getNameReferenceElement();
    if (nameReferenceElement == null) return null;

    AnnotationDocGenerator anno = new AnnotationDocGenerator(annotation, nameReferenceElement, context);
    if (anno.isNonDocumentedAnnotation()) return null;

    if (!(shownAnnotations.add(annotation.getQualifiedName()) ||
          JavaDocInfoGenerator.isRepeatableAnnotationType(nameReferenceElement.resolve()))) {
      return null;
    }
    return anno;
  }
}

enum AnnotationFormat {
  ToolTip, JavaDocShort, JavaDocComplete
}
