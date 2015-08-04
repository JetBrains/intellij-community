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
package com.intellij.psi.stubs;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.ParserDefinition;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.IStubFileElementType;
import com.intellij.util.io.PersistentHashMapValueStorage;

public class CumulativeStubVersion {
  private static final int VERSION = 30  + (PersistentHashMapValueStorage.COMPRESSION_ENABLED ? 1:0);

  public static int getCumulativeVersion() {
    int version = VERSION;
    for (final FileType fileType : FileTypeRegistry.getInstance().getRegisteredFileTypes()) {
      if (fileType instanceof LanguageFileType) {
        Language l = ((LanguageFileType)fileType).getLanguage();
        ParserDefinition parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(l);
        if (parserDefinition != null) {
          final IFileElementType type = parserDefinition.getFileNodeType();
          if (type instanceof IStubFileElementType) {
            version += ((IStubFileElementType)type).getStubVersion();
          }
        }
      }

      BinaryFileStubBuilder builder = BinaryFileStubBuilders.INSTANCE.forFileType(fileType);
      if (builder != null) {
        version += builder.getStubVersion();
      }
    }
    return version;
  }
}
