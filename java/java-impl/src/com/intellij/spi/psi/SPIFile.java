/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.spi.psi;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.FileViewProvider;
import com.intellij.spi.SPIFileType;
import com.intellij.lang.spi.SPILanguage;
import org.jetbrains.annotations.NotNull;

/**
 * User: anna
 */
public class SPIFile extends PsiFileBase {
  public SPIFile(@NotNull FileViewProvider viewProvider) {
    super(viewProvider, SPILanguage.INSTANCE);
  }

  @NotNull
  @Override
  public FileType getFileType() {
    return SPIFileType.INSTANCE;
  }
}
