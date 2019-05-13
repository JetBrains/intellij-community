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

package com.intellij.psi.impl.file;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiDirectoryContainer;
import com.intellij.psi.impl.PsiManagerImpl;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PsiDirectoryFactoryImpl extends PsiDirectoryFactory {
  private final PsiManagerImpl myManager;

  public PsiDirectoryFactoryImpl(final PsiManagerImpl manager) {
    myManager = manager;
  }
  @NotNull
  @Override
  public PsiDirectory createDirectory(@NotNull final VirtualFile file) {
    return new PsiDirectoryImpl(myManager, file);
  }

  @Override
  @NotNull
  public String getQualifiedName(@NotNull final PsiDirectory directory, final boolean presentable) {
    if (presentable) {
      return FileUtil.getLocationRelativeToUserHome(directory.getVirtualFile().getPresentableUrl());
    }
    return "";
  }

  @Override
  public PsiDirectoryContainer getDirectoryContainer(@NotNull PsiDirectory directory) {
    return null;
  }

  @Override
  public boolean isPackage(@NotNull PsiDirectory directory) {
    return false;
  }

  @Override
  public boolean isValidPackageName(String name) {
    return true;
  }
}
