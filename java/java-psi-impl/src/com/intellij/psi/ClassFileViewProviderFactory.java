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

/*
 * @author max
 */
package com.intellij.psi;

import com.intellij.lang.Language;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.ContentBasedFileSubstitutor;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public class ClassFileViewProviderFactory implements FileViewProviderFactory{
  @Override
  public FileViewProvider createFileViewProvider(@NotNull final VirtualFile file, final Language language, @NotNull final PsiManager manager, final boolean physical) {
    // Define language for compiled file
    final ContentBasedFileSubstitutor[] processors = Extensions.getExtensions(ContentBasedFileSubstitutor.EP_NAME);
    for (ContentBasedFileSubstitutor processor : processors) {
      Language lang = processor.obtainLanguageForFile(file);
      if (lang != null) {
        FileViewProviderFactory factory = LanguageFileViewProviders.INSTANCE.forLanguage(language);
        return factory.createFileViewProvider(file, language, manager, physical);
      }
    }

    return new ClassFileViewProvider(manager, file, physical);
  }
}