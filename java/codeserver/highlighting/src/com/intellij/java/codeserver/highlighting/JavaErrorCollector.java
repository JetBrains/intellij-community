// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeserver.highlighting;

import com.intellij.java.codeserver.highlighting.errors.JavaCompilationError;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * A starting point to highlight errors in Java code
 */
public final class JavaErrorCollector {
  private final JavaErrorVisitor myVisitor;

  /**
   * Create a collector to collect Java errors. After creation every single PSI element in the desired range should be passed into
   * {@link #processElement(PsiElement)} to actually gather errors
   * 
   * @param psiFile Java file to process
   * @param consumer a consumer to get errors
   */
  public JavaErrorCollector(@NotNull PsiFile psiFile, @NotNull Consumer<@NotNull JavaCompilationError<?, ?>> consumer) {
    myVisitor = new JavaErrorVisitor(psiFile, consumer);
  }

  /**
   * Finds the errors related to a given element. One must call this method for every element recursively in the desired range 
   * to get all the errors.
   * 
   * @param element element to find the errors at.
   */
  public void processElement(@NotNull PsiElement element) {
    element.accept(myVisitor);
  }

  /**
   * @param element element to check
   * @return the first compilation error while processing the specified element (without processing its children)
   */
  public static @Nullable JavaCompilationError<?, ?> findSingleError(@NotNull PsiElement element) {
    var processor = new Consumer<JavaCompilationError<?, ?>>() {
      JavaCompilationError<?, ?> myError = null;
      
      @Override
      public void accept(JavaCompilationError<?, ?> error) {
        if (myError == null) {
          myError = error;
        }
      }
    };
    new JavaErrorCollector(element.getContainingFile(), processor).processElement(element);
    return processor.myError;
  }
}
