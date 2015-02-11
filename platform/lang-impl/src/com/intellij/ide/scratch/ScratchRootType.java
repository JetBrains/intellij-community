/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.ide.scratch;

import com.intellij.icons.AllIcons;
import com.intellij.lang.Language;
import com.intellij.lang.PerFileMappings;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.LanguageSubstitutors;
import com.intellij.ui.LayeredIcon;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author gregsh
 */
public final class ScratchRootType extends RootType {

  @NotNull
  public static ScratchRootType getInstance() {
    return findByClass(ScratchRootType.class);
  }

  ScratchRootType() {
    super("scratches", "Scratches");
  }

  @Override
  public Language substituteLanguage(@NotNull Project project, @NotNull VirtualFile file) {
    PerFileMappings<Language> mapping = ScratchFileService.getInstance().getScratchesMapping();
    Language language = mapping.getMapping(file);
    return language != null && language != ScratchFileType.INSTANCE.getLanguage() ?
           LanguageSubstitutors.INSTANCE.substituteLanguage(language, file, project) : language;
  }

  @Nullable
  @Override
  public Icon substituteIcon(@NotNull Project project, @NotNull VirtualFile file) {
    Icon icon = ObjectUtils.chooseNotNull(super.substituteIcon(project, file), ScratchFileType.INSTANCE.getIcon());
    return LayeredIcon.create(icon, AllIcons.Actions.Scratch);
  }
}
