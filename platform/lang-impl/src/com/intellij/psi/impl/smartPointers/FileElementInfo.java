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
package com.intellij.psi.impl.smartPointers;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;

/**
* User: cdr
*/
class FileElementInfo implements SmartPointerElementInfo {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.smartPointers.FileElementInfo");
  private PsiFile myFile;
  private final Project myProject;

  public FileElementInfo(@NotNull PsiFile file) {
    LOG.assertTrue(file.isPhysical());
    myFile = file;

    myProject = myFile.getProject();
  }

  public Document getDocumentToSynchronize() {
    return null;
  }

  public void documentAndPsiInSync() {
  }

  public PsiElement restoreElement() {
    myFile = SelfElementInfo.restoreFile(myFile, myProject);
    return myFile;
  }
}
