// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethodWithResultObject;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NonNls;

/**
 * @author Pavel.Dolgov
 */
class ExtractMethodWithResultObjectProcessor {
  private static final Logger LOG = Logger.getInstance(ExtractMethodWithResultObjectProcessor.class);

  private final Project myProject;
  private final Editor myEditor;
  private final PsiElement[] myElements;

  @NonNls static final String REFACTORING_NAME = "Extract Method With Result Object";

  ExtractMethodWithResultObjectProcessor(@NonNls Project project, @NonNls Editor editor, @NonNls PsiElement[] elements) {
    myProject = project;
    myEditor = editor;
    myElements = elements;
  }

  Project getProject() {
    return myProject;
  }

  Editor getEditor() {
    return myEditor;
  }

  boolean prepare() {
    LOG.info("prepare");
    return true;
  }

  boolean showDialog() {
    LOG.info("showDialog");
    return true;
  }

  void doRefactoring() {
    LOG.info("doRefactoring");
  }
}
