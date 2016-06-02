/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.psi.stubsHierarchy.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiFileWithStubSupport;
import com.intellij.psi.stubs.IStubElementType;
import org.jetbrains.annotations.NotNull;

public class ClassAnchorUtil {

  public static PsiClass retrieve(Project project, SmartClassAnchor anchor) {
    if (anchor instanceof SmartClassAnchor.DirectSmartClassAnchor) {
      return ((SmartClassAnchor.DirectSmartClassAnchor)anchor).myPsiClass;
    }
    if (anchor instanceof SmartClassAnchor.StubSmartClassAnchor) {
      SmartClassAnchor.StubSmartClassAnchor stubAnchor = (SmartClassAnchor.StubSmartClassAnchor)anchor;
      VirtualFile file = PersistentFS.getInstance().findFileById(stubAnchor.myFileId);
      PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
      return (PsiClass) restoreFromStubIndex((PsiFileWithStubSupport)psiFile, stubAnchor.myStubId, stubAnchor.myStubElementType);
    }
    return null;
  }

  public static PsiClass retrieveInReadAction(final Project project, final SmartClassAnchor anchor) {
    return ApplicationManager.getApplication().runReadAction(new Computable<PsiClass>() {
      @Override
      public PsiClass compute() {
        return retrieve(project, anchor);
      }
    });
  }

  private static PsiElement restoreFromStubIndex(@NotNull PsiFileWithStubSupport fileImpl, int index, IStubElementType stubElementType) {
    return PsiAnchor.restoreFromStubIndex(fileImpl, index, stubElementType, false);
  }
}
