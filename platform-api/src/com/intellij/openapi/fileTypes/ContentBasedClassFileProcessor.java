package com.intellij.openapi.fileTypes;

import com.intellij.lang.Language;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Perfroms additional analyses on file with {@link com.intellij.openapi.fileTypes.StdFileTypes#CLASS} filetype (e. g. classfile,
 * compiled from other than Java source language).
 *
 * @author ilyas
 */
public interface ContentBasedClassFileProcessor {

  ExtensionPointName<ContentBasedClassFileProcessor> EP_NAME = ExtensionPointName.create("com.intellij.contentBasedClassFileProcessor");

  /**
   * Checks whether appropriate specific activity is available on given file
   */
  boolean isApplicable(Project project, VirtualFile vFile);

  /**
   * @return syntax highlighter for recognized classfile
   */
  @NotNull
  SyntaxHighlighter createHighlighter(Project project, VirtualFile vFile);

  /**
   * @return specific text representation of compiled classfile
   */
  @NotNull
  String obtainFileText(Project project, VirtualFile file);

  /**
   * @return language for compiled classfile
   */
  @Nullable
  Language obtainLanguageForFile(VirtualFile file);


}
