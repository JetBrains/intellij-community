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
package com.intellij.psi.impl.compiled;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.fileTypes.BinaryFileDecompiler;
import com.intellij.openapi.project.DefaultProjectFactory;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.compiled.ClassFileDecompilers;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author max
 */
public class ClassFileDecompiler implements BinaryFileDecompiler {
  /** @deprecated temporary solution, to remove in IDEA 14 */
  public interface PlatformDecompiler {
    @Nullable
    CharSequence decompile(@NotNull VirtualFile file);
  }

  @Override
  @NotNull
  public CharSequence decompile(@NotNull VirtualFile file) {
    ClassFileDecompilers.Decompiler decompiler = ClassFileDecompilers.find(file);
    if (decompiler instanceof ClassFileDecompilers.Full) {
      PsiManager manager = PsiManager.getInstance(DefaultProjectFactory.getInstance().getDefaultProject());
      return ((ClassFileDecompilers.Full)decompiler).createFileViewProvider(file, manager, true).getContents();
    }

    @SuppressWarnings("deprecation") PlatformDecompiler platformDecompiler = ServiceManager.getService(PlatformDecompiler.class);
    if (platformDecompiler != null) {
      CharSequence result = platformDecompiler.decompile(file);
      if (result != null) return result;
    }

    return decompileText(file);
  }

  @NotNull
  public static CharSequence decompileText(@NotNull VirtualFile file) {
    ClassFileDecompilers.Decompiler decompiler = ClassFileDecompilers.find(file);
    if (decompiler instanceof ClassFileDecompilers.Light) {
      return ((ClassFileDecompilers.Light)decompiler).getText(file);
    }

    return ClsFileImpl.decompile(file);
  }
}