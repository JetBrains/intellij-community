/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.codeInsight;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class ReadableExternalAnnotationsManager extends BaseExternalAnnotationsManager {
  @NotNull private volatile ThreeState myHasAnyAnnotationsRoots = ThreeState.UNSURE;

  public ReadableExternalAnnotationsManager(PsiManager psiManager) {
    super(psiManager);
  }

  @Override
  protected boolean hasAnyAnnotationsRoots() {
    if (myHasAnyAnnotationsRoots == ThreeState.UNSURE) {
      final Module[] modules = ModuleManager.getInstance(myPsiManager.getProject()).getModules();
      for (Module module : modules) {
        for (OrderEntry entry : ModuleRootManager.getInstance(module).getOrderEntries()) {
          final String[] urls = AnnotationOrderRootType.getUrls(entry);
          if (urls.length > 0) {
            myHasAnyAnnotationsRoots = ThreeState.YES;
            return true;
          }
        }
      }
      myHasAnyAnnotationsRoots = ThreeState.NO;
    }
    return myHasAnyAnnotationsRoots == ThreeState.YES;
  }

  @Override
  @NotNull
  protected List<VirtualFile> getExternalAnnotationsRoots(@NotNull VirtualFile libraryFile) {
    ProjectFileIndex fileIndex = ProjectRootManager.getInstance(myPsiManager.getProject()).getFileIndex();
    Set<VirtualFile> result = new LinkedHashSet<VirtualFile>();
    for (OrderEntry entry : fileIndex.getOrderEntriesForFile(libraryFile)) {
      if (!(entry instanceof ModuleOrderEntry)) {
        Collections.addAll(result, AnnotationOrderRootType.getFiles(entry));
      }
    }
    return new ArrayList<VirtualFile>(result);
  }

  @Override
  protected void dropCache() {
    myHasAnyAnnotationsRoots = ThreeState.UNSURE;
    super.dropCache();
  }
}
