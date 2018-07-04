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
package com.intellij.openapi.roots.impl;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.module.EffectiveLanguageLevelUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.FileAttribute;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * @author Gregory.Shrago
 */
public class JavaLanguageLevelPusher implements FilePropertyPusher<LanguageLevel> {

  public static void pushLanguageLevel(@NotNull final Project project) {
    PushedFilePropertiesUpdater instance = PushedFilePropertiesUpdater.getInstance(project);
    FilePropertyPusher[] extensions = EP_NAME.getExtensions();
    for (FilePropertyPusher pusher : extensions) {
      if (pusher instanceof JavaLanguageLevelPusher) {
        instance.pushAll(pusher);
      }
    }
  }

  @Override
  public void initExtra(@NotNull Project project, @NotNull MessageBus bus, @NotNull Engine languageLevelUpdater) {
    // nothing
  }

  @Override
  @NotNull
  public Key<LanguageLevel> getFileDataKey() {
    return LanguageLevel.KEY;
  }

  @Override
  public boolean pushDirectoriesOnly() {
    return true;
  }

  @Override
  @NotNull
  public LanguageLevel getDefaultValue() {
    return LanguageLevel.HIGHEST;
  }

  @Override
  public LanguageLevel getImmediateValue(@NotNull Project project, @Nullable VirtualFile file) {
    return null;
  }

  @Override
  public LanguageLevel getImmediateValue(@NotNull Module module) {
    return EffectiveLanguageLevelUtil.getEffectiveLanguageLevel(module);
  }

  @Override
  public boolean acceptsFile(@NotNull VirtualFile file) {
    return false;
  }

  @Override
  public boolean acceptsDirectory(@NotNull VirtualFile file, @NotNull Project project) {
    return ProjectFileIndex.SERVICE.getInstance(project).isInSourceContent(file);
  }

  private static final FileAttribute PERSISTENCE = new FileAttribute("language_level_persistence", 3, true);

  @Override
  public void persistAttribute(@NotNull Project project, @NotNull VirtualFile fileOrDir, @NotNull LanguageLevel level) throws IOException {
    final DataInputStream iStream = PERSISTENCE.readAttribute(fileOrDir);
    if (iStream != null) {
      try {
        final int oldLevelOrdinal = DataInputOutputUtil.readINT(iStream);
        if (oldLevelOrdinal == level.ordinal()) return;
      }
      finally {
        iStream.close();
      }
    }

    try (DataOutputStream oStream = PERSISTENCE.writeAttribute(fileOrDir)) {
      DataInputOutputUtil.writeINT(oStream, level.ordinal());
    }

    // Todo: GwtLanguageLevelPusher changes java language level for single files without firing filePropertiesChanged
    // so code below doesn't work.
    // Uncomment it and remove older code once the problem is fixed
    //PushedFilePropertiesUpdater.getInstance(project).filePropertiesChanged(fileOrDir, f -> isJavaLike(f.getFileType()));

    for (VirtualFile child : fileOrDir.getChildren()) {
      if (!child.isDirectory() && isJavaLike(child.getFileType())) {
        PushedFilePropertiesUpdater.getInstance(project).filePropertiesChanged(child);
      }
    }
  }

  private static boolean isJavaLike(FileType type) {
    return type instanceof LanguageFileType && ((LanguageFileType)type).getLanguage().isKindOf(JavaLanguage.INSTANCE);
  }

  @Override
  public void afterRootsChanged(@NotNull Project project) {
  }

  @Nullable
  public String getInconsistencyLanguageLevelMessage(@NotNull String message, @NotNull PsiElement element,
                                                     @NotNull LanguageLevel level, @NotNull PsiFile file) {
    return null;
  }
}
