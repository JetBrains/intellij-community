/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.ide.actions;

import com.intellij.ide.util.gotoByName.ChooseByNameBase;
import com.intellij.ide.util.gotoByName.DefaultChooseByNameItemProvider;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
* @author peter
*/
public class GotoFileItemProvider extends DefaultChooseByNameItemProvider {
  private final Project myProject;

  public GotoFileItemProvider(@NotNull Project project, @Nullable PsiElement context) {
    super(context);
    myProject = project;
  }

  @Override
  public boolean filterElements(@NotNull ChooseByNameBase base,
                                @NotNull String pattern,
                                boolean everywhere,
                                @NotNull ProgressIndicator indicator,
                                @NotNull Processor<Object> consumer) {
    if (pattern.contains("/") || pattern.contains("\\")) {
      File ioFile = new File(pattern);
      VirtualFile vFile = ioFile.exists() ? LocalFileSystem.getInstance().refreshAndFindFileByIoFile(ioFile) : null;
      PsiFile psiFile = vFile == null ? null : PsiManager.getInstance(myProject).findFile(vFile);
      if (psiFile != null && !consumer.process(psiFile)) {
        return false;
      }
    }

    return super.filterElements(base, pattern, everywhere, indicator, consumer);
  }
}
