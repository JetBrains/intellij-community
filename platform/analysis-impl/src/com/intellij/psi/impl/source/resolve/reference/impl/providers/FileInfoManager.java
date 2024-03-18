// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.file.FileLookupInfoProvider;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

@Service
public final class FileInfoManager {
  public static FileInfoManager getFileInfoManager() {
    return ApplicationManager.getApplication().getService(FileInfoManager.class);
  }

  public static Object getFileLookupItem(PsiElement psiElement) {
    if (!(psiElement instanceof PsiFile file) || !(psiElement.isPhysical())) {
      return psiElement;
    }

    return _getLookupItem(file, file.getName(), file.getIcon(0));
  }

  public static @Nullable String getFileAdditionalInfo(PsiElement psiElement) {
    return _getInfo(psiElement);
  }

  private static @Nullable String _getInfo(PsiElement psiElement) {
    if (!(psiElement instanceof PsiFile psiFile) || !(psiElement.isPhysical())) {
      return null;
    }

    FileLookupInfoProvider provider =
      ContainerUtil.find(FileLookupInfoProvider.EP_NAME.getExtensionList(),
                         p -> ArrayUtil.find(p.getFileTypes(), psiFile.getFileType()) != -1);

    if (provider != null) {
      VirtualFile virtualFile = psiFile.getVirtualFile();
      if (virtualFile != null) {
        Pair<String, String> info = provider.getLookupInfo(virtualFile, psiElement.getProject());
        return Pair.getSecond(info);
      }
    }

    return null;
  }

  public static LookupElementBuilder getFileLookupItem(PsiElement psiElement, String encoded, Icon icon) {
    if (!(psiElement instanceof PsiFile) || !(psiElement.isPhysical())) {
      return LookupElementBuilder.create(psiElement, encoded).withIcon(icon);
    }
    return _getLookupItem((PsiFile)psiElement, encoded, icon);
  }

  public static LookupElementBuilder _getLookupItem(final @NotNull PsiFile file, String name, Icon icon) {
    LookupElementBuilder builder = LookupElementBuilder.create(file, name).withIcon(icon);

    final String info = _getInfo(file);
    if (info != null) {
      return builder.withTailText(String.format(" (%s)", info), true);
    }

    return builder;
  }
}
