// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.presentation.java;

import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.ItemPresentationProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiJavaModule;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.light.LightJavaModule;
import com.intellij.ui.IconManager;
import com.intellij.ui.PlatformIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class JavaModulePresentationProvider implements ItemPresentationProvider<PsiJavaModule> {
  private static final Pattern JAR_NAME = Pattern.compile(".+/([^/]+\\.jar)!/.*");

  @Override
  public ItemPresentation getPresentation(@NotNull PsiJavaModule item) {
    return new ItemPresentation() {
      @Override
      public String getPresentableText() {
        return item.getName();
      }

      @Override
      public @Nullable String getLocationString() {
        VirtualFile file = PsiImplUtil.getModuleVirtualFile(item);
        FileIndexFacade index = FileIndexFacade.getInstance(item.getProject());
        if (index.isInLibraryClasses(file)) {
          Matcher matcher = JAR_NAME.matcher(file.getPath());
          if (matcher.find()) {
            return matcher.group(1);
          }
        }
        else if (index.isInSource(file)) {
          Module module = index.getModuleForFile(file);
          if (module != null) {
            return '[' + module.getName() + ']';
          }
        }
        return null;
      }

      @Override
      public Icon getIcon(boolean unused) {
        return IconManager.getInstance().getPlatformIcon(item instanceof LightJavaModule ? PlatformIcons.ArchiveFileType : PlatformIcons.JavaModule);
      }
    };
  }
}