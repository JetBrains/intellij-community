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
