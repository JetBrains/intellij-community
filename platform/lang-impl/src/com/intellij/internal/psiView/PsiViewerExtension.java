// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.internal.psiView;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public interface PsiViewerExtension {
  ExtensionPointName<PsiViewerExtension> EP_NAME = ExtensionPointName.create("com.intellij.psiViewerExtension");

  @Nls(capitalization = Nls.Capitalization.Title)
  String getName();

  Icon getIcon();

  PsiElement createElement(Project project, String text);

  @NotNull
  FileType getDefaultFileType();
}
