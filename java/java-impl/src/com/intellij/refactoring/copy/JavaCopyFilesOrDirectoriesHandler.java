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
package com.intellij.refactoring.copy;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.codeInsight.daemon.impl.CollectHighlightsUtil;

/**
 * @author yole
 */
public class JavaCopyFilesOrDirectoriesHandler extends CopyFilesOrDirectoriesHandler {
  @Override
  protected boolean canCopyFile(final PsiFile element) {
    if (element instanceof PsiClassOwner &&
        PsiUtilBase.getTemplateLanguageFile(element) != element &&
        !CollectHighlightsUtil.isOutsideSourceRoot(element)) {
      return false;
    }
    return true;
  }

  @Override
  protected boolean canCopyDirectory(PsiDirectory element) {
    return !hasPackages(element);
  }

  public static boolean hasPackages(PsiDirectory directory) {
    if (JavaDirectoryService.getInstance().getPackage(directory) != null) {
      return true;
    }
    return false;
  }
}
