// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.javadoc;

import com.intellij.codeInsight.AnnotationTargetUtil;
import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.psi.*;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.util.XmlStringUtil;
import one.util.streamex.StreamEx;
import org.intellij.lang.annotations.Flow;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class AnnotationDocGenerator {
  private static final Logger LOG = Logger.getInstance(AnnotationDocGenerator.class);
  @NotNull private final PsiAnnotation myAnnotation;
  @NotNull private final PsiJavaCodeReferenceElement myNameReference;
  @NotNull private final PsiElement myContext;
  @Nullable private final PsiClass myTargetClass;
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

  public boolean isInferred() {
    return AnnotationUtil.isInferredAnnotation(myAnnotation);
  }

  void generateAnnotation(StringBuilder buffer, AnnotationFormat format) {
    String qualifiedName = myAnnotation.getQualifiedName();
    PsiClassType type = myTargetClass != null && qualifiedName != null &&
                        JavaDocUtil.findReferenceTarget(myContext.getManager(), qualifiedName, myContext) != null
                        ? JavaPsiFacade.getElementFactory(myContext.getProject()).createType(myTargetClass, PsiSubstitutor.EMPTY)
                        : null;

    boolean red = type == null && !myResolveNotPossible && !isInferred() && !isExternal();

    boolean highlightNonCodeAnnotations = format == AnnotationFormat.ToolTip && (isInferred() || isExternal());
    if (highlightNonCodeAnnotations) buffer.append("<b>");
    if (isInferred()) buffer.append("<i>");
    if (red) buffer.append("<font color=red>");

    boolean generateLink = format != AnnotationFormat.ToolTip;
    boolean forceShortNames = format != AnnotationFormat.JavaDocComplete;

    buffer.append("@");
    String name = forceShortNames ? myNameReference.getReferenceName() : myNameReference.getText();
    if (type != null && generateLink) {
      JavaDocInfoGenerator.generateLink(buffer, myTargetClass, name, format == AnnotationFormat.JavaDocComplete);
    } else {
      buffer.append(name);
    }
    if (red) buffer.append("</font>");

    generateAnnotationAttributes(buffer, generateLink);
    if (isInferred()) buffer.append("</i>");
    if (highlightNonCodeAnnotations) buffer.append("</b>");
  }

  private void generateAnnotationAttributes(StringBuilder buffer, boolean generateLink) {
    final PsiNameValuePair[] attributes = myAnnotation.getParameterList().getAttributes();
    if (attributes.length > 0) {
      buffer.append("(");
      boolean first = true;
      for (PsiNameValuePair pair : attributes) {
        if (!first) buffer.append(",&nbsp;");
        first = false;
        generateAnnotationAttribute(buffer, generateLink, pair);
      }
      buffer.append(")");
    }
  }

  private static void generateAnnotationAttribute(StringBuilder buffer, boolean generateLink, PsiNameValuePair pair) {
    final String name = pair.getName();
    if (name != null) {
      buffer.append(name);
      buffer.append(" = ");
    }
    final PsiAnnotationMemberValue value = pair.getValue();
    if (value != null) {
      if (value instanceof PsiArrayInitializerMemberValue) {
        buffer.append("{");
        boolean firstMember = true;
        for(PsiAnnotationMemberValue memberValue:((PsiArrayInitializerMemberValue)value).getInitializers()) {
          if (!firstMember) buffer.append(",");
          firstMember = false;
          appendLinkOrText(buffer, memberValue, generateLink);
        }
        buffer.append("}");
      }
      else {
        appendLinkOrText(buffer, value, generateLink);
      }
    }
  }

  private static void appendLinkOrText(StringBuilder buffer,
                                       PsiAnnotationMemberValue memberValue,
                                       boolean generateLink) {
    if (generateLink && memberValue instanceof PsiQualifiedReferenceElement) {
      String text = ((PsiQualifiedReferenceElement)memberValue).getCanonicalText();
      PsiElement resolve = null;
      try {
        resolve = ((PsiQualifiedReferenceElement)memberValue).resolve();
      }
      catch (Exception e) {
        LOG.debug(e);
      }

      if (resolve instanceof PsiField) {
        PsiField field = (PsiField)resolve;
        PsiClass aClass = field.getContainingClass();
        int startOfPropertyNamePosition = text.lastIndexOf('.');

        if (startOfPropertyNamePosition != -1) {
          text = text.substring(0, startOfPropertyNamePosition) + '#' + text.substring(startOfPropertyNamePosition + 1);
        }
        else {
          if (aClass != null) text = aClass.getQualifiedName() + '#' + field.getName();
        }
        JavaDocInfoGenerator.generateLink(buffer, text, aClass != null? aClass.getName() + '.' + field.getName():null, memberValue, false);
        return;
      }
    }

    buffer.append(XmlStringUtil.escapeString(memberValue.getText()));
  }

  public static List<AnnotationDocGenerator> getAnnotationsToShow(@NotNull PsiAnnotationOwner owner, @NotNull PsiElement context) {
    if (owner instanceof PsiModifierList) {
      return getAnnotationsToShow(((PsiModifierListOwner)((PsiModifierList)owner).getParent()));
    }
    Set<String> shownAnnotations = new HashSet<>();
    return ContainerUtil.mapNotNull(owner.getAnnotations(),
                                    annotation -> forAnnotation(context, shownAnnotations, annotation));
  }

  public static List<AnnotationDocGenerator> getAnnotationsToShow(@NotNull PsiModifierListOwner owner) {
    Set<String> shownAnnotations = new HashSet<>();
    return StreamEx.of(AnnotationUtil.getAllAnnotations(owner, false, null))
      .filter(owner instanceof PsiClass || owner instanceof PsiJavaModule ? anno -> true
                                                                          : anno -> !AnnotationTargetUtil.isTypeAnnotation(anno))
      .map(annotation -> forAnnotation(owner, shownAnnotations, annotation))
      .nonNull()
      .toList();
  }

  private static @Nullable AnnotationDocGenerator forAnnotation(@NotNull PsiElement context,
                                                                @NotNull Set<String> shownAnnotations,
                                                                @NotNull PsiAnnotation annotation) {
    PsiJavaCodeReferenceElement nameReferenceElement = annotation.getNameReferenceElement();
    if (nameReferenceElement == null) return null;

    AnnotationDocGenerator anno = new AnnotationDocGenerator(annotation, nameReferenceElement, context);
    if (anno.isNonDocumentedAnnotation()) return null;

    if (!(shownAnnotations.add(annotation.getQualifiedName()) || JavaDocInfoGenerator.isRepeatableAnnotationType(annotation))) return null;
    return anno;
  }
}

enum AnnotationFormat {
  ToolTip, JavaDocShort, JavaDocComplete
}
