// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.daemon.impl;

import com.intellij.diagnostic.PluginException;
import com.intellij.lang.ASTNode;
import com.intellij.lang.annotation.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.IndexNotReadyException;
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
import java.util.concurrent.atomic.AtomicReference;

/**
 * Use {@link AnnotationHolder} instead. The members of this class can suddenly change or disappear.
 */
@ApiStatus.Internal
public final class AnnotationHolderImpl extends SmartList<@NotNull Annotation> implements AnnotationHolder {
  private static final Logger LOG = Logger.getInstance(AnnotationHolderImpl.class);
  private final AnnotationSession myAnnotationSession;

  private final boolean myBatchMode;
  private final AtomicReference<PsiElement> myCurrentElement = new AtomicReference<>();
  private final @NotNull Object/*Annotator|ExternalAnnotator*/ myAnnotator;
  /**
   * @deprecated Do not instantiate the AnnotationHolderImpl directly, please use the one provided via {@link Annotator#annotate(PsiElement, AnnotationHolder)} instead
   */
  @ApiStatus.Internal
  @Deprecated
  public AnnotationHolderImpl(@NotNull AnnotationSession session, boolean batchMode) {
    myAnnotationSession = session;
    myBatchMode = batchMode;
    myAnnotator = (Annotator)(element, holder) -> {};
  }

  @ApiStatus.Internal
  AnnotationHolderImpl(@NotNull Object/*Annotator|ExternalAnnotator*/ annotator, @NotNull AnnotationSession session, boolean batchMode) {
    myAnnotator = annotator;
    myAnnotationSession = session;
    myBatchMode = batchMode;
    if (!(annotator instanceof Annotator) && !(annotator instanceof ExternalAnnotator<?,?>)) {
      throw new IllegalArgumentException("annotator must be instanceof Annotator|ExternalAnnotator but got "+annotator.getClass());
    }
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
    PsiFile psiFile = myAnnotationSession.getFile();
    PsiFile containingFile = node.getContainingFile();
    LOG.assertTrue(containingFile != null, node);
    VirtualFile containingVFile = containingFile.getVirtualFile();
    VirtualFile myVFile = psiFile.getVirtualFile();
    if (!Comparing.equal(containingVFile, myVFile)) {
      LOG.error(
        "Annotation must be registered for an element inside '" + psiFile + "' which is in '" + myVFile + "'.\n" +
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

  @Contract(pure = true)
  private static @Nullable String wrapXml(@Nullable String message) {
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
  @Deprecated
  private @NotNull Annotation doCreateAnnotation(@NotNull HighlightSeverity severity,
                                                 @NotNull TextRange range,
                                                 @NlsContexts.DetailedDescription @Nullable String message,
                                                 @NlsContexts.Tooltip @Nullable String tooltip,
                                                 @Nullable Class<?> callerClass,
                                                 @NotNull String methodName) {
    Annotation annotation = new Annotation(range.getStartOffset(), range.getEndOffset(), severity, message, tooltip);
    add(annotation);
    String callerInfo = callerClass == null ? "" : " (the call to which was found in "+callerClass+")";
    PluginException pluginException = PluginException.createByClass(
      new IncorrectOperationException("'AnnotationHolder."+methodName + "()' method" + callerInfo + " is slow, non-incremental " +
                                      "and thus can cause unexpected behaviour (e.g. annoying blinking), " +
                                      "is deprecated and will be removed soon. " +
                                      "Please use `newAnnotation(...).create()` instead"), callerClass == null ? getClass() : callerClass);
    LOG.warnInProduction(pluginException);
    return annotation;
  }

  @Override
  public @NotNull AnnotationSession getCurrentAnnotationSession() {
    return myAnnotationSession;
  }

  @Override
  public @NotNull AnnotationBuilder newAnnotation(@NotNull HighlightSeverity severity, @NotNull @Nls String message) {
    return createBuilder(severity, message);
  }
  @Override
  public @NotNull AnnotationBuilder newSilentAnnotation(@NotNull HighlightSeverity severity) {
    return createBuilder(severity, null);
  }

  private @NotNull AnnotationBuilder createBuilder(@NotNull HighlightSeverity severity, @Nls String message) {
    return new B(this, severity, message, myCurrentElement.get(), myAnnotator);
  }

  @ApiStatus.Internal
  public void runAnnotatorWithContext(@NotNull PsiElement element) {
    myCurrentElement.set(element);
    try {
      ((Annotator)myAnnotator).annotate(element, this);
    }
    catch (IndexNotReadyException ignore) { }
  }

  /**
   * @deprecated use {@link #runAnnotatorWithContext(PsiElement)}
   */
  @ApiStatus.Internal
  @Deprecated(forRemoval = true)
  public void runAnnotatorWithContext(PsiElement element, Annotator annotator) {
    myCurrentElement.set(element);
    try {
      annotator.annotate(element, this);
    }
    catch (IndexNotReadyException ignore) { }
  }

  @ApiStatus.Internal
  public <R> void applyExternalAnnotatorWithContext(@NotNull PsiFile psiFile, R result) {
    myCurrentElement.set(psiFile);
    //noinspection unchecked
    ((ExternalAnnotator<?,R>)myAnnotator).apply(psiFile, result, this);
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
