/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.diagnostic.PluginException;
import com.intellij.lang.ASTNode;
import com.intellij.lang.annotation.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ReflectionUtilRt;
import com.intellij.util.SmartList;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Use {@link AnnotationHolder} instead. The members of this class can suddenly change or disappear.
 */
@ApiStatus.Internal
public class AnnotationHolderImpl extends SmartList<Annotation> implements AnnotationHolder {
  private static final Logger LOG = Logger.getInstance(AnnotationHolderImpl.class);
  private final AnnotationSession myAnnotationSession;

  private final boolean myBatchMode;
  Annotator myCurrentAnnotator;

  /**
   * @deprecated Do not instantiate the AnnotationHolderImpl directly, please use the one provided to {@link Annotator#annotate(PsiElement, AnnotationHolder)} instead
   */
  @ApiStatus.Internal
  @Deprecated
  public AnnotationHolderImpl(@NotNull AnnotationSession session) {
    this(session, false);
    PluginException.reportDeprecatedUsage("AnnotationHolderImpl(AnnotationSession)", "Please use the AnnotationHolder passed to Annotator.annotate() instead");
  }

  /**
   * @deprecated Do not instantiate the AnnotationHolderImpl directly, please use the one provided to {@link Annotator#annotate(PsiElement, AnnotationHolder)} instead
   */
  @ApiStatus.Internal
  @Deprecated
  public AnnotationHolderImpl(@NotNull AnnotationSession session, boolean batchMode) {
    myAnnotationSession = session;
    myBatchMode = batchMode;
  }

  @Override
  public boolean isBatchMode() {
    return myBatchMode;
  }

  @Override
  public Annotation createErrorAnnotation(@NotNull PsiElement elt, String message) {
    assertMyFile(elt);
    Class<?> callerClass = ReflectionUtilRt.findCallerClass(2);
    return doCreateAnnotation(HighlightSeverity.ERROR, elt.getTextRange(), message, wrapXml(message), callerClass, "createErrorAnnotation");
  }

  @Override
  public Annotation createErrorAnnotation(@NotNull ASTNode node, String message) {
    assertMyFile(node.getPsi());
    Class<?> callerClass = ReflectionUtilRt.findCallerClass(2);
    return doCreateAnnotation(HighlightSeverity.ERROR, node.getTextRange(), message, wrapXml(message), callerClass, "createErrorAnnotation");
  }

  @Override
  public Annotation createErrorAnnotation(@NotNull TextRange range, String message) {
    Class<?> callerClass = ReflectionUtilRt.findCallerClass(2);
    return doCreateAnnotation(HighlightSeverity.ERROR, range, message, wrapXml(message), callerClass, "createErrorAnnotation");
  }

  @Override
  public Annotation createWarningAnnotation(@NotNull PsiElement elt, String message) {
    assertMyFile(elt);
    Class<?> callerClass = ReflectionUtilRt.findCallerClass(2);
    return doCreateAnnotation(HighlightSeverity.WARNING, elt.getTextRange(), message, wrapXml(message), callerClass, "createWarningAnnotation");
  }

  @Override
  public Annotation createWarningAnnotation(@NotNull ASTNode node, String message) {
    assertMyFile(node.getPsi());
    Class<?> callerClass = ReflectionUtilRt.findCallerClass(2);
    return doCreateAnnotation(HighlightSeverity.WARNING, node.getTextRange(), message, wrapXml(message), callerClass, "createWarningAnnotation");
  }

  @Override
  public Annotation createWarningAnnotation(@NotNull TextRange range, String message) {
    Class<?> callerClass = ReflectionUtilRt.findCallerClass(2);
    return doCreateAnnotation(HighlightSeverity.WARNING, range, message, wrapXml(message), callerClass, "createWarningAnnotation");
  }

  @Override
  public Annotation createWeakWarningAnnotation(@NotNull PsiElement elt, @Nullable String message) {
    assertMyFile(elt);
    Class<?> callerClass = ReflectionUtilRt.findCallerClass(2);
    return doCreateAnnotation(HighlightSeverity.WEAK_WARNING, elt.getTextRange(), message, wrapXml(message), callerClass, "createWeakWarningAnnotation");
  }

  @Override
  public Annotation createWeakWarningAnnotation(@NotNull ASTNode node, @Nullable String message) {
    assertMyFile(node.getPsi());
    Class<?> callerClass = ReflectionUtilRt.findCallerClass(2);
    return doCreateAnnotation(HighlightSeverity.WEAK_WARNING, node.getTextRange(), message, wrapXml(message), callerClass, "createWeakWarningAnnotation");
  }

  @Override
  public Annotation createWeakWarningAnnotation(@NotNull TextRange range, String message) {
    Class<?> callerClass = ReflectionUtilRt.findCallerClass(2);
    return doCreateAnnotation(HighlightSeverity.WEAK_WARNING, range, message, wrapXml(message), callerClass, "createWeakWarningAnnotation");
  }

  @Override
  public Annotation createInfoAnnotation(@NotNull PsiElement elt, String message) {
    assertMyFile(elt);
    Class<?> callerClass = ReflectionUtilRt.findCallerClass(2);
    return doCreateAnnotation(HighlightSeverity.INFORMATION, elt.getTextRange(), message, wrapXml(message), callerClass, "createInfoAnnotation");
  }

  @Override
  public Annotation createInfoAnnotation(@NotNull ASTNode node, String message) {
    assertMyFile(node.getPsi());
    Class<?> callerClass = ReflectionUtilRt.findCallerClass(2);
    return doCreateAnnotation(HighlightSeverity.INFORMATION, node.getTextRange(), message, wrapXml(message), callerClass, "createInfoAnnotation");
  }

  private void assertMyFile(PsiElement node) {
    if (node == null) return;
    PsiFile myFile = myAnnotationSession.getFile();
    PsiFile containingFile = node.getContainingFile();
    LOG.assertTrue(containingFile != null, node);
    VirtualFile containingVFile = containingFile.getVirtualFile();
    VirtualFile myVFile = myFile.getVirtualFile();
    if (!Comparing.equal(containingVFile, myVFile)) {
      LOG.error(
        "Annotation must be registered for an element inside '" + myFile + "' which is in '" + myVFile + "'.\n" +
        "Element passed: '" + node + "' is inside the '" + containingFile + "' which is in '" + containingVFile + "'");
    }
  }

  @Override
  public Annotation createInfoAnnotation(@NotNull TextRange range, String message) {
    Class<?> callerClass = ReflectionUtilRt.findCallerClass(2);
    return doCreateAnnotation(HighlightSeverity.INFORMATION, range, message, wrapXml(message), callerClass, "createInfoAnnotation");
  }

  @Override
  public Annotation createAnnotation(@NotNull HighlightSeverity severity, @NotNull TextRange range, @Nullable String message) {
    Class<?> callerClass = ReflectionUtilRt.findCallerClass(2);
    return doCreateAnnotation(severity, range, message, wrapXml(message), callerClass, "createAnnotation");
  }

  @Nullable
  private static @NonNls String wrapXml(@Nullable String message) {
    return message == null ? null : XmlStringUtil.wrapInHtml(XmlStringUtil.escapeString(message));
  }

  @Override
  public Annotation createAnnotation(@NotNull HighlightSeverity severity, @NotNull TextRange range, @Nullable String message,
                                     @Nullable @NlsContexts.Tooltip String tooltip) {
    Class<?> callerClass = ReflectionUtilRt.findCallerClass(2);
    return doCreateAnnotation(severity, range, message, tooltip, callerClass, "createAnnotation");
  }

  /**
   * @deprecated this is an old way of creating annotations, via createXXXAnnotation(). please use newAnnotation() instead
   */
  @NotNull
  @Deprecated
  private Annotation doCreateAnnotation(@NotNull HighlightSeverity severity,
                                        @NotNull TextRange range,
                                        @Nullable String message,
                                        @Nullable String tooltip,
                                        @Nullable Class<?> callerClass,
                                        @NotNull String methodName) {
    Annotation annotation = new Annotation(range.getStartOffset(), range.getEndOffset(), severity, message, tooltip);
    add(annotation);
    String callerInfo = callerClass == null ? "" : " (the call to which was found in "+callerClass+")";
    PluginException pluginException = PluginException.createByClass(
      new IncorrectOperationException("'AnnotationHolder."+methodName + "()' method" + callerInfo + " is slow, non-incremental " +
                                      "and thus can cause unexpected behaviour (e.g. annoying blinking), " +
                                      "is deprecated and will be removed soon. " +
                                      "Please use `newAnnotation().create()` instead"), callerClass == null ? getClass() : callerClass);
    // temporary fix, CLion guys promised to fix their annotator eventually
    if ("com.jetbrains.cidr.lang.daemon.OCAnnotator".equals(callerClass == null ? null : callerClass.getName())) {
      //todo
      //LOG.warnInProduction(pluginException);
      LOG.warn(pluginException);
    }
    else {
      LOG.warnInProduction(pluginException);
    }
    return annotation;
  }

  public boolean hasAnnotations() {
    return !isEmpty();
  }

  @NotNull
  @Override
  public AnnotationSession getCurrentAnnotationSession() {
    return myAnnotationSession;
  }

  // internal optimization method to reduce latency between creating Annotation and showing it on screen
  // (Do not) call this method to
  // 1. state that all Annotations in this holder are final (no further Annotation.setXXX() or .registerFix() are following) and
  // 2. queue them all for converting to RangeHighlighters in EDT
  @ApiStatus.Internal
  void queueToUpdateIncrementally() {
  }

  @NotNull
  @Override
  public AnnotationBuilder newAnnotation(@NotNull HighlightSeverity severity, @NotNull @Nls String message) {
    return new B(this, severity, message, myCurrentElement);
  }
  @NotNull
  @Override
  public AnnotationBuilder newSilentAnnotation(@NotNull HighlightSeverity severity) {
    return new B(this, severity, null, myCurrentElement);
  }

  PsiElement myCurrentElement;
  @ApiStatus.Internal
  public void runAnnotatorWithContext(@NotNull PsiElement element, @NotNull Annotator annotator) {
    myCurrentElement = element;
    annotator.annotate(element, this);
    myCurrentElement = null;
  }
  @ApiStatus.Internal
  public <R> void applyExternalAnnotatorWithContext(@NotNull PsiFile file, @NotNull ExternalAnnotator<?,R> annotator, R result) {
    myCurrentElement = file;
    annotator.apply(file, result, this);
    myCurrentElement = null;
  }

  // to assert each AnnotationBuilder did call .create() in the end
  private final List<B> myCreatedAnnotationBuilders = new ArrayList<>();
  void annotationBuilderCreated(@NotNull B builder) {
    synchronized (myCreatedAnnotationBuilders) {
      myCreatedAnnotationBuilders.add(builder);
    }
  }
  public void assertAllAnnotationsCreated() {
    synchronized (myCreatedAnnotationBuilders) {
      try {
        for (B builder : myCreatedAnnotationBuilders) {
          builder.assertAnnotationCreated();
        }
      }
      finally {
        myCreatedAnnotationBuilders.clear();
      }
    }
  }

  void annotationCreatedFrom(@NotNull B builder) {
    synchronized (myCreatedAnnotationBuilders) {
      myCreatedAnnotationBuilders.remove(builder);
    }
  }
}
