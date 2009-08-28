package com.intellij.lang;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Avdeev
 */
public interface LanguageProvider {

  ExtensionPointName<LanguageProvider> EP_NAME = new ExtensionPointName<LanguageProvider>("com.intellij.lang.languageProvider");
  
  @Nullable
  Language getLanguage(final @NotNull VirtualFile file, final @NotNull Project project);
}
