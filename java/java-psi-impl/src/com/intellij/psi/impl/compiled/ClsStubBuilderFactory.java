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
package com.intellij.psi.impl.compiled;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.stubs.PsiFileStub;
import com.intellij.util.cls.ClsFormatException;

/** @deprecated API no longer supported, use {@link com.intellij.psi.compiled.ClassFileDecompilers} instead (to remove in IDEA 14) */
@SuppressWarnings({"deprecation", "unused"})
public abstract class ClsStubBuilderFactory<T extends PsiFile> {
  public static final ExtensionPointName<ClsStubBuilderFactory> EP_NAME = ExtensionPointName.create("com.intellij.clsStubBuilderFactory");

  public abstract PsiFileStub<T> buildFileStub(final VirtualFile file, byte[] bytes) throws ClsFormatException;

  public PsiFileStub<T> buildFileStub(final VirtualFile file, byte[] bytes, Project project) throws ClsFormatException {
    return buildFileStub(file, bytes);
  }

  public abstract boolean canBeProcessed(final VirtualFile file, byte[] bytes);

  public abstract boolean isInnerClass(final VirtualFile file);

  public int getStubVersion() {
    return 1;
  }
}
