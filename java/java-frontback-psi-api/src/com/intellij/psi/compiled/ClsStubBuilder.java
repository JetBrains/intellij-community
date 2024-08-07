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
package com.intellij.psi.compiled;

import com.intellij.psi.stubs.PsiFileStub;
import com.intellij.util.cls.ClsFormatException;
import com.intellij.util.indexing.FileContent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class ClsStubBuilder {
  /**
   * Non-zero positive number expected.
   */
  public abstract int getStubVersion();

  /**
   * May return {@code null} for inner or synthetic classes - i.e. those indexed as a part of their parent .class file.
   */
  @Nullable
  public abstract PsiFileStub<?> buildFileStub(@NotNull FileContent fileContent) throws ClsFormatException;
}
