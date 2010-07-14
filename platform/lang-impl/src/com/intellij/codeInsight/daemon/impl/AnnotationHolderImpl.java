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

package com.intellij.codeInsight.daemon.impl;

import com.intellij.lang.ASTNode;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.util.SmartList;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author max
 */
public class AnnotationHolderImpl extends SmartList<Annotation> implements AnnotationHolder {
  public Annotation createErrorAnnotation(@NotNull PsiElement elt, String message) {
    return createAnnotation(elt.getTextRange(), HighlightSeverity.ERROR, message);
  }

  public Annotation createErrorAnnotation(@NotNull ASTNode node, String message) {
    return createAnnotation(node.getTextRange(), HighlightSeverity.ERROR, message);
  }

  public Annotation createErrorAnnotation(@NotNull TextRange range, String message) {
    return createAnnotation(range, HighlightSeverity.ERROR, message);
  }

  public Annotation createWarningAnnotation(@NotNull PsiElement elt, String message) {
    return createAnnotation(elt.getTextRange(), HighlightSeverity.WARNING, message);
  }

  public Annotation createWarningAnnotation(@NotNull ASTNode node, String message) {
    return createAnnotation(node.getTextRange(), HighlightSeverity.WARNING, message);
  }

  public Annotation createWarningAnnotation(@NotNull TextRange range, String message) {
    return createAnnotation(range, HighlightSeverity.WARNING, message);
  }

  public Annotation createInformationAnnotation(@NotNull PsiElement elt, String message) {
    return createAnnotation(elt.getTextRange(), HighlightSeverity.INFO, message);
  }

  public Annotation createInformationAnnotation(@NotNull ASTNode node, String message) {
    return createAnnotation(node.getTextRange(), HighlightSeverity.INFO, message);
  }

  public Annotation createInformationAnnotation(@NotNull TextRange range, String message) {
    return createAnnotation(range, HighlightSeverity.INFO, message);
  }

  public Annotation createInfoAnnotation(@NotNull PsiElement elt, String message) {
    return createAnnotation(elt.getTextRange(), HighlightSeverity.INFORMATION, message);
  }

  public Annotation createInfoAnnotation(@NotNull ASTNode node, String message) {
    return createAnnotation(node.getTextRange(), HighlightSeverity.INFORMATION, message);
  }

  public Annotation createInfoAnnotation(@NotNull TextRange range, String message) {
    return createAnnotation(range, HighlightSeverity.INFORMATION, message);
  }

  protected Annotation createAnnotation(TextRange range, HighlightSeverity severity, String message) {
    //noinspection HardCodedStringLiteral
    //TODO: FIXME
    String tooltip = message == null ? null : "<html><body>" + XmlStringUtil.escapeString(message) + "</body></html>";
    Annotation annotation = new Annotation(range.getStartOffset(), range.getEndOffset(), severity, message, tooltip);
    add(annotation);
    return annotation;
  }

  public boolean hasAnnotations() {
    return !isEmpty();
  }
}
