/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

/*
 * @author max
 */
package com.intellij.psi.impl.search;

import com.intellij.lang.StdLanguages;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;

public class JavaLikeSourceFilterScope extends JavaSourceFilterScope {
  private final GlobalSearchScope myDelegate;
  private final ProjectFileIndex myIndex;

  public JavaLikeSourceFilterScope(final GlobalSearchScope delegate, final Project project) {
    super(delegate, project);
    myDelegate = delegate;
    myIndex = ProjectRootManager.getInstance(project).getFileIndex();
  }

  @Override
  public boolean contains(final VirtualFile file) {
    if (myDelegate != null && !myDelegate.contains(file)) {
      return false;
    }
    final FileType fileType = file.getFileType();
    return isJavaLikeFile(fileType) && myIndex.isInSourceContent(file) ||
           StdFileTypes.CLASS == fileType && myIndex.isInLibraryClasses(file);
  }

  private static boolean isJavaLikeFile(final FileType fileType) {
    return StdFileTypes.JAVA == fileType ||
           fileType instanceof LanguageFileType &&
           ((LanguageFileType)fileType).getLanguage().isKindOf(StdLanguages.JAVA);
  }
}
