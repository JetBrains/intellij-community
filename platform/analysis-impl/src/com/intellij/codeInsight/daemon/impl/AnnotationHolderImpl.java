// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

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
import com.intellij.util.ObjectUtils;
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
  private ExternalAnnotator<?, ?> myExternalAnnotator;

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
  public Annotation createErrorAnnotation(@NotNull PsiElement elt, @NlsContexts.DetailedDescription String message) {
    assertMyFile(elt);
    Class<?> callerClass = ReflectionUtilRt.findCallerClass(2);
    return doCreateAnnotation(HighlightSeverity.ERROR, elt.getTextRange(), message, wrapXml(message), callerClass, "createErrorAnnotation");
  }

  @Override
  public Annotation createErrorAnnotation(@NotNull ASTNode node, @NlsContexts.DetailedDescription String message) {
    assertMyFile(node.getPsi());
    Class<?> callerClass = ReflectionUtilRt.findCallerClass(2);
    return doCreateAnnotation(HighlightSeverity.ERROR, node.getTextRange(), message, wrapXml(message), callerClass, "createErrorAnnotation");
  }

  @Override
  public Annotation createErrorAnnotation(@NotNull TextRange range, @NlsContexts.DetailedDescription String message) {
    Class<?> callerClass = ReflectionUtilRt.findCallerClass(2);
    return doCreateAnnotation(HighlightSeverity.ERROR, range, message, wrapXml(message), callerClass, "createErrorAnnotation");
  }

  @Override
  public Annotation createWarningAnnotation(@NotNull PsiElement elt, @NlsContexts.DetailedDescription String message) {
    assertMyFile(elt);
    Class<?> callerClass = ReflectionUtilRt.findCallerClass(2);
    return doCreateAnnotation(HighlightSeverity.WARNING, elt.getTextRange(), message, wrapXml(message), callerClass, "createWarningAnnotation");
  }

  @Override
  public Annotation createWarningAnnotation(@NotNull ASTNode node, @NlsContexts.DetailedDescription String message) {
    assertMyFile(node.getPsi());
    Class<?> callerClass = ReflectionUtilRt.findCallerClass(2);
    return doCreateAnnotation(HighlightSeverity.WARNING, node.getTextRange(), message, wrapXml(message), callerClass, "createWarningAnnotation");
  }

  @Override
  public Annotation createWarningAnnotation(@NotNull TextRange range, @NlsContexts.DetailedDescription String message) {
    Class<?> callerClass = ReflectionUtilRt.findCallerClass(2);
    return doCreateAnnotation(HighlightSeverity.WARNING, range, message, wrapXml(message), callerClass, "createWarningAnnotation");
  }

  @Override
  public Annotation createWeakWarningAnnotation(@NotNull PsiElement elt, @NlsContexts.DetailedDescription @Nullable String message) {
    assertMyFile(elt);
    Class<?> callerClass = ReflectionUtilRt.findCallerClass(2);
    return doCreateAnnotation(HighlightSeverity.WEAK_WARNING, elt.getTextRange(), message, wrapXml(message), callerClass, "createWeakWarningAnnotation");
  }

  @Override
  public Annotation createWeakWarningAnnotation(@NotNull ASTNode node, @NlsContexts.DetailedDescription @Nullable String message) {
    assertMyFile(node.getPsi());
    Class<?> callerClass = ReflectionUtilRt.findCallerClass(2);
    return doCreateAnnotation(HighlightSeverity.WEAK_WARNING, node.getTextRange(), message, wrapXml(message), callerClass, "createWeakWarningAnnotation");
  }

  @Override
  public Annotation createWeakWarningAnnotation(@NotNull TextRange range, @NlsContexts.DetailedDescription String message) {
    Class<?> callerClass = ReflectionUtilRt.findCallerClass(2);
    return doCreateAnnotation(HighlightSeverity.WEAK_WARNING, range, message, wrapXml(message), callerClass, "createWeakWarningAnnotation");
  }

  @Override
  public Annotation createInfoAnnotation(@NotNull PsiElement elt, @NlsContexts.DetailedDescription String message) {
    assertMyFile(elt);
    Class<?> callerClass = ReflectionUtilRt.findCallerClass(2);
    return doCreateAnnotation(HighlightSeverity.INFORMATION, elt.getTextRange(), message, wrapXml(message), callerClass, "createInfoAnnotation");
  }

  @Override
  public Annotation createInfoAnnotation(@NotNull ASTNode node, @NlsContexts.DetailedDescription String message) {
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
  public Annotation createInfoAnnotation(@NotNull TextRange range, @NlsContexts.DetailedDescription String message) {
    Class<?> callerClass = ReflectionUtilRt.findCallerClass(2);
    return doCreateAnnotation(HighlightSeverity.INFORMATION, range, message, wrapXml(message), callerClass, "createInfoAnnotation");
  }

  @Override
  public Annotation createAnnotation(@NotNull HighlightSeverity severity, @NotNull TextRange range, @Nullable @NlsContexts.DetailedDescription String message) {
    Class<?> callerClass = ReflectionUtilRt.findCallerClass(2);
    return doCreateAnnotation(severity, range, message, wrapXml(message), callerClass, "createAnnotation");
  }

  @Nullable
  @Contract(pure = true)
  private static String wrapXml(@Nullable String message) {
    return message == null ? null : XmlStringUtil.wrapInHtml(XmlStringUtil.escapeString(message));
  }

  @Override
  public Annotation createAnnotation(@NotNull HighlightSeverity severity, @NotNull TextRange range, @NlsContexts.DetailedDescription @Nullable String message,
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
                                        @NlsContexts.DetailedDescription @Nullable String message,
                                        @NlsContexts.Tooltip @Nullable String tooltip,
                                        @Nullable Class<?> callerClass,
                                        @NotNull String methodName) {
    Annotation annotation = new Annotation(range.getStartOffset(), range.getEndOffset(), severity, message, tooltip);
    add(annotation);

    if (isCLionAnnotator(callerClass)) {
      if (!isWarningReportedForCLionAlready) {
        //todo temporary fix, CLion guys promised to fix their annotator eventually
        //Log warning only once. Otherwise, log in tests get flooded with hundreds of warnings
        //noinspection AssignmentToStaticFieldFromInstanceMethod
        isWarningReportedForCLionAlready = true;
        reportNewAPIWarning(callerClass, methodName, false);
      }
    }
    else {
      reportNewAPIWarning(callerClass, methodName, true);
    }
    return annotation;
  }


  private void reportNewAPIWarning(@Nullable Class<?> callerClass, @NotNull String methodName, boolean reportInProduction) {
    String callerInfo = callerClass == null ? "" : " (the call to which was found in " + callerClass + ")";
    PluginException pluginException = PluginException.createByClass(
      new IncorrectOperationException("'AnnotationHolder." + methodName + "()' method" + callerInfo + " is slow, non-incremental " +
                                      "and thus can cause unexpected behaviour (e.g. annoying blinking), " +
                                      "is deprecated and will be removed soon. " +
                                      "Please use `newAnnotation().create()` instead"), callerClass == null ? getClass() : callerClass);
    if (reportInProduction) {
      LOG.warnInProduction(pluginException);
    }
    else {
      LOG.warn(pluginException);
    }
  }

  private static volatile boolean isWarningReportedForCLionAlready = false;
  private static boolean isCLionAnnotator(@Nullable Class<?> callerClass) {
    return callerClass != null && "com.jetbrains.cidr.lang.daemon.OCAnnotator".equals(callerClass.getName());
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
    return new B(this, severity, message, myCurrentElement, ObjectUtils.chooseNotNull(myCurrentAnnotator, myExternalAnnotator));
  }
  @NotNull
  @Override
  public AnnotationBuilder newSilentAnnotation(@NotNull HighlightSeverity severity) {
    return new B(this, severity, null, myCurrentElement, ObjectUtils.chooseNotNull(myCurrentAnnotator, myExternalAnnotator));
  }

  PsiElement myCurrentElement;
  @ApiStatus.Internal
  public void runAnnotatorWithContext(@NotNull PsiElement element, @NotNull Annotator annotator) {
    myCurrentAnnotator = annotator;
    myCurrentElement = element;
    annotator.annotate(element, this);
    myCurrentElement = null;
    myCurrentAnnotator = null;
  }
  @ApiStatus.Internal
  public <R> void applyExternalAnnotatorWithContext(@NotNull PsiFile file, @NotNull ExternalAnnotator<?,R> annotator, R result) {
    myExternalAnnotator = annotator;
    myCurrentElement = file;
    annotator.apply(file, result, this);
    myCurrentElement = null;
    myExternalAnnotator = null;
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
