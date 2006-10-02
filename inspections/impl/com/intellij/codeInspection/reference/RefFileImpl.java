/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.codeInspection.reference;

import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.*;
import org.jetbrains.annotations.Nullable;


public class RefFileImpl extends RefElementImpl implements RefFile {
  RefFileImpl(PsiFile elem, RefManager manager) {
    super(elem, manager);
    if (elem instanceof PsiJavaFile) {
      ((RefPackageImpl)manager.getPackage(((PsiJavaFile)elem).getPackageName())).add(this);
    } else {
      final Project project = elem.getProject();
      final ProjectRootManager projectRootManager = ProjectRootManager.getInstance(project);
      final ProjectFileIndex fileIndex = projectRootManager.getFileIndex();
      final VirtualFile vFile = elem.getVirtualFile();
      if (vFile == null) return;
      final VirtualFile parentDirectory = vFile.getParent();
      if (parentDirectory == null) return;
      final PsiDirectory psiDirectory = PsiManager.getInstance(project).findDirectory(parentDirectory);
      if (psiDirectory != null){
        final PsiPackage aPackage = psiDirectory.getPackage();
        if (aPackage != null){
          final Module module = fileIndex.getModuleForFile(parentDirectory);
          if (module == null) return;
          final ContentEntry[] contentEntries = ModuleRootManager.getInstance(module).getContentEntries();
          for (ContentEntry contentEntry : contentEntries) {
            final VirtualFile contentEntryFile = contentEntry.getFile();
            if (contentEntryFile == null) continue; //invalid entry
            if (VfsUtil.isAncestor(contentEntryFile, parentDirectory, false)){
              final SourceFolder[] sourceFolderFiles = contentEntry.getSourceFolders();
              for (SourceFolder folder : sourceFolderFiles) {
                final VirtualFile folderFile = folder.getFile();
                if (folderFile == null) continue; //invalid source path
                if (VfsUtil.isAncestor(folderFile, parentDirectory, false)){
                  String qualifiedName = aPackage.getQualifiedName();
                  final int prefixLength = folder.getPackagePrefix().length();
                  if (prefixLength > 0 && qualifiedName.length() > prefixLength){ //consider package prefixes
                    ((RefPackageImpl)manager.getPackage(qualifiedName.substring(prefixLength + 1))).add(this);
                  } else {
                    if (qualifiedName.length() == 0) {
                      qualifiedName = InspectionsBundle.message("inspection.reference.default.package");
                    }
                    ((RefPackageImpl)manager.getPackage(qualifiedName)).add(this);
                  }
                  return;
                }
              }
            }
          }
        }
      }
    }
  }

  public PsiFile getElement() {
    return (PsiFile)super.getElement();
  }

  public void accept(RefVisitor visitor) {
    visitor.visitFile(this);
  }

  @Nullable
  public String getAccessModifier() {
    return null;
  }

  protected void initialize() {
    ((RefManagerImpl)getRefManager()).fireNodeInitialized(this);
  }

  @Nullable
  public static RefElement fileFromExternalName(final RefManager manager, final String fqName) {
    final VirtualFile virtualFile = VirtualFileManager.getInstance().findFileByUrl(fqName);
    if (virtualFile != null) {
      final PsiFile psiFile = PsiManager.getInstance(manager.getProject()).findFile(virtualFile);
      if (psiFile != null) {
        return manager.getReference(psiFile);
      }
    }
    return null;  
  }
}
