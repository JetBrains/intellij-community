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
import com.intellij.lang.annotation.AnnotationSession;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.SmartList;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author max
 */
public class AnnotationHolderImpl extends SmartList<Annotation> implements AnnotationHolder {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.AnnotationHolderImpl");
  private AnnotationSession myAnnotationSession;

  AnnotationHolderImpl() {
  }
  public AnnotationHolderImpl(@NotNull AnnotationSession session) {
    myAnnotationSession = session;
  }

  public Annotation createErrorAnnotation(@NotNull PsiElement elt, String message) {
    assertMyFile(elt);
    return createAnnotation(elt.getTextRange(), HighlightSeverity.ERROR, message);
  }

  public Annotation createErrorAnnotation(@NotNull ASTNode node, String message) {
    assertMyFile(node.getPsi());
    return createAnnotation(node.getTextRange(), HighlightSeverity.ERROR, message);
  }

  public Annotation createErrorAnnotation(@NotNull TextRange range, String message) {
    return createAnnotation(range, HighlightSeverity.ERROR, message);
  }

  public Annotation createWarningAnnotation(@NotNull PsiElement elt, String message) {
    assertMyFile(elt);
    return createAnnotation(elt.getTextRange(), HighlightSeverity.WARNING, message);
  }

  public Annotation createWarningAnnotation(@NotNull ASTNode node, String message) {
    assertMyFile(node.getPsi());
    return createAnnotation(node.getTextRange(), HighlightSeverity.WARNING, message);
  }

  public Annotation createWarningAnnotation(@NotNull TextRange range, String message) {
    return createAnnotation(range, HighlightSeverity.WARNING, message);
  }

  public Annotation createInformationAnnotation(@NotNull PsiElement elt, String message) {
    assertMyFile(elt);
    return createAnnotation(elt.getTextRange(), HighlightSeverity.INFO, message);
  }

  public Annotation createInformationAnnotation(@NotNull ASTNode node, String message) {
    assertMyFile(node.getPsi());
    return createAnnotation(node.getTextRange(), HighlightSeverity.INFO, message);
  }

  public Annotation createInformationAnnotation(@NotNull TextRange range, String message) {
    return createAnnotation(range, HighlightSeverity.INFO, message);
  }

  @Override
  public Annotation createWeakWarningAnnotation(@NotNull PsiElement elt, @Nullable String message) {
    assertMyFile(elt);
    return createAnnotation(elt.getTextRange(), HighlightSeverity.WEAK_WARNING, message);
  }

  @Override
  public Annotation createWeakWarningAnnotation(@NotNull ASTNode node, @Nullable String message) {
    assertMyFile(node.getPsi());
    return createAnnotation(node.getTextRange(), HighlightSeverity.WEAK_WARNING, message);
  }

  @Override
  public Annotation createWeakWarningAnnotation(@NotNull TextRange range, String message) {
    return createAnnotation(range, HighlightSeverity.WEAK_WARNING, message);
  }

  public Annotation createInfoAnnotation(@NotNull PsiElement elt, String message) {
    assertMyFile(elt);
    return createAnnotation(elt.getTextRange(), HighlightSeverity.INFORMATION, message);
  }

  public Annotation createInfoAnnotation(@NotNull ASTNode node, String message) {
    assertMyFile(node.getPsi());
    return createAnnotation(node.getTextRange(), HighlightSeverity.INFORMATION, message);
  }

  private void assertMyFile(PsiElement node) {
    if (node == null) return;
    PsiFile myFile = myAnnotationSession.getFile();
    PsiFile containingFile = node.getContainingFile();
    LOG.assertTrue(containingFile != null, node);
    VirtualFile containingVFile = containingFile.getVirtualFile();
    VirtualFile myVFile = myFile.getVirtualFile();
    if (containingVFile != myVFile) {
      LOG.error(
        "Annotation must be registered for an element inside '" + myFile + "' which is in '" + myVFile + "'.\n" +
        "Element passed: '" + node + "' is inside the '" + containingFile + "' which is in '" + containingVFile + "'");
    }
  }

  public Annotation createInfoAnnotation(@NotNull TextRange range, String message) {
    return createAnnotation(range, HighlightSeverity.INFORMATION, message);
  }

  protected Annotation createAnnotation(TextRange range, HighlightSeverity severity, @Nullable String message) {
    //noinspection HardCodedStringLiteral
    //TODO: FIXME
    @NonNls
    String tooltip = message == null ? null : "<html><body>" + XmlStringUtil.escapeString(message) + "</body></html>";
    Annotation annotation = new Annotation(range.getStartOffset(), range.getEndOffset(), severity, message, tooltip);
    add(annotation);
    return annotation;
  }

  public boolean hasAnnotations() {
    return !isEmpty();
  }

  void setSession(@NotNull AnnotationSession annotationSession) {
    myAnnotationSession = annotationSession;
  }

  @Override
  @NotNull("it's not null during highlighting")
  public AnnotationSession getCurrentAnnotationSession() {
    return myAnnotationSession;
  }

  @Override
  public void clear() {
    super.clear();
    myAnnotationSession = null;
  }
}
