/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.fileTypes;

import com.intellij.codeFormatting.PseudoTextBuilder;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;

import javax.swing.*;

public interface FileType {
  FileType[] EMPTY_ARRAY = new FileType[0];

  String getName();

  String getDescription();

  String getDefaultExtension();

  Icon getIcon();

  boolean isBinary();

  boolean isReadOnly();

  String getCharset(VirtualFile file);

  FileHighlighter getHighlighter(Project project);

  PsiFile createPsiFile(VirtualFile file, Project project);

  PsiFile createPsiFile(Project project, String name, char[] text, int startOffset, int endOffset);

  FileTypeSupportCapabilities getSupportCapabilities();


  PseudoTextBuilder getPseudoTextBuilder();

  StructureViewModel getStructureViewModel(VirtualFile file, Project project);
}
