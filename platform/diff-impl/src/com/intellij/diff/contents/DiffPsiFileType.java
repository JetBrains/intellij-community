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
package com.intellij.diff.contents;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoFilter;
import com.intellij.codeInsight.daemon.impl.IntentionActionFilter;
import com.intellij.codeInsight.daemon.impl.analysis.DefaultHighlightingSettingProvider;
import com.intellij.codeInsight.daemon.impl.analysis.FileHighlightingSetting;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.icons.AllIcons;
import com.intellij.lang.Language;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.fileTypes.*;
import com.intellij.openapi.fileTypes.ex.FileTypeIdentifiableByVirtualFile;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.LanguageSubstitutor;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class DiffPsiFileType extends LanguageFileType implements FileTypeIdentifiableByVirtualFile, PeripheralFileType {
  public static final Key<FileType> ORIGINAL_FILE_TYPE_KEY = Key.create("Diff.DiffPsiFileType.OriginalFileTypeKey");
  public static final LanguageFileType INSTANCE = new DiffPsiFileType();

  DiffPsiFileType() {
    super(PlainTextLanguage.INSTANCE);
  }

  @Override
  public boolean isMyFileType(@NotNull VirtualFile file) {
    return isDiffFile(file);
  }

  @NotNull
  @Override
  public String getName() {
    return "Diff";
  }

  @NotNull
  @Override
  public String getDescription() {
    return "Diff";
  }

  @NotNull
  @Override
  public String getDefaultExtension() {
    return "";
  }

  @Nullable
  @Override
  public Icon getIcon() {
    return AllIcons.Diff.Diff;
  }

  @Override
  public boolean isReadOnly() {
    return true;
  }

  @Nullable
  @Override
  public String getCharset(@NotNull VirtualFile file, @NotNull byte[] content) {
    return null;
  }


  public static class TypeFactory extends FileTypeFactory {
    @Override
    public void createFileTypes(@NotNull FileTypeConsumer consumer) {
      consumer.consume(INSTANCE);
    }
  }

  public static class Substitutor extends LanguageSubstitutor {
    @Nullable
    @Override
    public Language getLanguage(@NotNull VirtualFile file, @NotNull Project project) {
      FileType originalType = getOriginalFileType(file);
      if (originalType instanceof LanguageFileType) {
        return ((LanguageFileType)originalType).getLanguage();
      }
      return null;
    }
  }

  public static class HighlightFilter implements HighlightInfoFilter {
    @Override
    public boolean accept(@NotNull HighlightInfo info, @Nullable PsiFile file) {
      if (!isDiffFile(file)) return true;
      if (info.getSeverity() == HighlightSeverity.ERROR) return false;
      return true;
    }
  }

  public static class IntentionFilter implements IntentionActionFilter {
    @Override
    public boolean accept(@NotNull IntentionAction intentionAction, @Nullable PsiFile file) {
      return !isDiffFile(file);
    }
  }

  public static class HighlightingSettingProvider extends DefaultHighlightingSettingProvider {
    @Nullable
    @Override
    public FileHighlightingSetting getDefaultSetting(@NotNull Project project, @NotNull VirtualFile file) {
      if (!isDiffFile(file)) return null;
      return FileHighlightingSetting.SKIP_INSPECTION;
    }
  }


  private static boolean isDiffFile(@Nullable PsiFile file) {
    return file != null && isDiffFile(file.getVirtualFile());
  }

  private static boolean isDiffFile(@Nullable VirtualFile file) {
    return file != null && getOriginalFileType(file) != null;
  }

  @Nullable
  private static FileType getOriginalFileType(@NotNull VirtualFile file) {
    FileType fileType = file.getUserData(ORIGINAL_FILE_TYPE_KEY);
    return fileType instanceof DiffPsiFileType ? null : fileType;
  }
}
