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
package com.intellij.codeInsight.javadoc;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.util.XmlStringUtil;
import org.intellij.lang.annotations.Flow;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class AnnotationDocGenerator {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.javadoc.AnnotationGenerator");
  @NotNull private final PsiAnnotation myAnnotation;
  @NotNull private final PsiJavaCodeReferenceElement myNameReference;
  @NotNull private final PsiModifierListOwner myOwner;
  @Nullable private final PsiClass myTargetClass;
  private final boolean myResolveNotPossible;

  private AnnotationDocGenerator(@NotNull PsiAnnotation annotation, @NotNull PsiJavaCodeReferenceElement nameReference, @NotNull PsiModifierListOwner owner) {
     myAnnotation = annotation;
     myNameReference = nameReference;
     myOwner = owner;
    
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

  boolean isInferred() {
    return AnnotationUtil.isInferredAnnotation(myAnnotation);
  }

  public boolean isInferredFromSource() {
    return isInferred() && !(PsiUtil.preferCompiledElement(myOwner) instanceof PsiCompiledElement);
  }

  void generateAnnotation(StringBuilder buffer, AnnotationFormat format) {
    String qualifiedName = myAnnotation.getQualifiedName();
    PsiClassType type =
      myTargetClass != null && qualifiedName != null && JavaDocUtil.findReferenceTarget(myOwner.getManager(), qualifiedName, myOwner) != null
      ? JavaPsiFacade.getElementFactory(myOwner.getProject()).createType(myTargetClass, PsiSubstitutor.EMPTY)
      : null;
    
    boolean red = type == null && !myResolveNotPossible && !isInferred() && !isExternal();

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

  public static List<AnnotationDocGenerator> getAnnotationsToShow(@NotNull PsiModifierListOwner owner) {
    List<AnnotationDocGenerator> infos = new ArrayList<>();

    Set<String> shownAnnotations = ContainerUtil.newHashSet();

    for (PsiAnnotation annotation : AnnotationUtil.getAllAnnotations(owner, false, null)) {
      PsiJavaCodeReferenceElement nameReferenceElement = annotation.getNameReferenceElement();
      if (nameReferenceElement == null) continue;

      AnnotationDocGenerator anno = new AnnotationDocGenerator(annotation, nameReferenceElement, owner);
      
      if (anno.isNonDocumentedAnnotation()) continue;
      
      if (!(shownAnnotations.add(annotation.getQualifiedName()) || JavaDocInfoGenerator.isRepeatableAnnotationType(annotation))) continue;

      infos.add(anno);
    }
    return infos;
  }
}

enum AnnotationFormat {
  ToolTip, JavaDocShort, JavaDocComplete
}
