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

package com.intellij.ide.util;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public abstract class TreeFileChooserFactory {
  public static TreeFileChooserFactory getInstance(Project project) {
    return ServiceManager.getService(project, TreeFileChooserFactory.class);
  }

  @NotNull
  public abstract TreeFileChooser createFileChooser(@NotNull String title,
                                                    @Nullable PsiFile initialFile,
                                                    @Nullable FileType fileType,
                                                    @Nullable TreeFileChooser.PsiFileFilter filter);


  @NotNull
  public abstract TreeFileChooser createFileChooser(@NotNull String title,
                                                    @Nullable PsiFile initialFile,
                                                    @Nullable FileType fileType,
                                                    @Nullable TreeFileChooser.PsiFileFilter filter,
                                                    boolean disableStructureProviders);


  @NotNull
  public abstract TreeFileChooser createFileChooser(@NotNull String title,
                                                    @Nullable PsiFile initialFile,
                                                    @Nullable FileType fileType,
                                                    @Nullable TreeFileChooser.PsiFileFilter filter,
                                                    boolean disableStructureProviders,
                                                    boolean showLibraryContents);
}
