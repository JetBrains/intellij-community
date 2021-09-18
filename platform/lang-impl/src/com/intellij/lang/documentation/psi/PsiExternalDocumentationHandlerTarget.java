// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.documentation.psi;

import com.intellij.codeInsight.navigation.UtilKt;
import com.intellij.lang.documentation.DocumentationResult;
import com.intellij.lang.documentation.DocumentationTarget;
import com.intellij.lang.documentation.ExternalDocumentationHandler;
import com.intellij.model.Pointer;
import com.intellij.navigation.TargetPresentation;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPointerManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

final class PsiExternalDocumentationHandlerTarget implements DocumentationTarget {

  private final @NotNull PsiElement myTargetElement;
  private final @NotNull PsiExternalDocumentationHandlerTargetPointer myPointer;

  private PsiExternalDocumentationHandlerTarget(
    @NotNull PsiElement targetElement,
    @NotNull PsiExternalDocumentationHandlerTargetPointer pointer
  ) {
    myTargetElement = targetElement;
    myPointer = pointer;
  }

  PsiExternalDocumentationHandlerTarget(
    @NotNull ExternalDocumentationHandler handler,
    @NotNull String url,
    @NotNull PsiElement targetElement
  ) {
    this(
      targetElement,
      new PsiExternalDocumentationHandlerTargetPointer(
        handler,
        url,
        SmartPointerManager.createPointer(targetElement)
      )
    );
  }

  @Override
  public @NotNull Pointer<? extends DocumentationTarget> createPointer() {
    return myPointer;
  }

  @NotNull
  @Override
  public TargetPresentation getPresentation() {
    return UtilKt.targetPresentation(myTargetElement);
  }

  @Override
  public @Nullable Navigatable getNavigatable() {
    return myTargetElement instanceof Navigatable ? (Navigatable)myTargetElement : null;
  }

  @NotNull
  @Override
  public DocumentationResult computeDocumentation() {
    return DocumentationResult.asyncDocumentation(fetchComputable(myPointer.myHandler, myPointer.myUrl, myTargetElement));
  }

  // static method ensures that this is not captured
  private static @NotNull Supplier<@Nullable DocumentationResult> fetchComputable(
    @NotNull ExternalDocumentationHandler handler,
    @NotNull String url,
    @NotNull PsiElement context
  ) {
    return () -> {
      String text = handler.fetchExternalDocumentation(url, context);
      String ref = handler.extractRefFromLink(url);
      return DocumentationResult.externalDocumentation(text, ref, url);
    };
  }

  private static final class PsiExternalDocumentationHandlerTargetPointer implements Pointer<PsiExternalDocumentationHandlerTarget> {

    final @NotNull ExternalDocumentationHandler myHandler;
    final @NotNull String myUrl;
    private final @NotNull Pointer<? extends PsiElement> myContextPointer;

    PsiExternalDocumentationHandlerTargetPointer(
      @NotNull ExternalDocumentationHandler handler,
      @NotNull String url,
      @NotNull Pointer<? extends PsiElement> pointer
    ) {
      myHandler = handler;
      myUrl = url;
      myContextPointer = pointer;
    }

    @Override
    public @Nullable PsiExternalDocumentationHandlerTarget dereference() {
      PsiElement context = myContextPointer.dereference();
      return context != null
             ? new PsiExternalDocumentationHandlerTarget(context, this)
             : null;
    }
  }
}
