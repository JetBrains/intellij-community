/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.psi.impl.source;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.LightVirtualFile;
import org.jetbrains.annotations.NotNull;

public class PsiJavaFileImpl extends PsiJavaFileBaseImpl {
  public PsiJavaFileImpl(FileViewProvider file) {
    super(JavaStubElementTypes.JAVA_FILE, JavaStubElementTypes.JAVA_FILE, file);
  }

  @NotNull
  @Override
  public GlobalSearchScope getResolveScope() {
    final VirtualFile file = getVirtualFile();
    if (file != null && !(file instanceof LightVirtualFile)) {
      final FileIndexFacade index = ServiceManager.getService(getProject(), FileIndexFacade.class);
      if (!index.isInSource(file) && !index.isInLibraryClasses(file)) {
        return GlobalSearchScope.fileScope(this);
      }
    }
    return super.getResolveScope();
  }

  @Override
  @NotNull
  public FileType getFileType() {
    return JavaFileType.INSTANCE;
  }

  @Override
  public String toString() {
    return "PsiJavaFile:" + getName();
  }
}
