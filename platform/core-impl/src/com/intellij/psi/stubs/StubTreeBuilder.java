/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.IStubFileElementType;
import com.intellij.util.indexing.FileContent;
import com.intellij.util.indexing.SubstitutedFileType;
import org.jetbrains.annotations.Nullable;

public class StubTreeBuilder {
  private static final Key<StubElement> stubElementKey = Key.create("stub.tree.for.file.content");

  private StubTreeBuilder() {
  }

  @Nullable
  public static StubElement buildStubTree(final FileContent inputData) {
    StubElement data = inputData.getUserData(stubElementKey);
    if (data != null) return data;

    //noinspection SynchronizationOnLocalVariableOrMethodParameter
    synchronized (inputData) {
      data = inputData.getUserData(stubElementKey);
      if (data != null) return data;

      final FileType fileType = inputData.getFileType();

      if (fileType.isBinary()) {
        final BinaryFileStubBuilder builder = BinaryFileStubBuilders.INSTANCE.forFileType(fileType);
        assert builder != null;

        data = builder.buildStubTree(inputData.getFile(), inputData.getContent(), inputData.getProject());
      }
      else {
        final LanguageFileType filetype = (LanguageFileType)fileType;
        Language l = filetype.getLanguage();
        final IFileElementType type = LanguageParserDefinitions.INSTANCE.forLanguage(l).getFileNodeType();

        PsiFile psi = inputData.getPsiFile();
        CharSequence contentAsText = inputData.getContentAsText();
        psi.putUserData(StubTreeLoader.FILE_TEXT_CONTENT_KEY, contentAsText);

        try {
          if (type instanceof IStubFileElementType) {
            data = ((IStubFileElementType)type).getBuilder().buildStubTree(psi);
          }
          else if (filetype instanceof SubstitutedFileType) {
            SubstitutedFileType substituted = (SubstitutedFileType) filetype;
            LanguageFileType original = (LanguageFileType)substituted.getOriginalFileType();
            final IFileElementType originalType = LanguageParserDefinitions.INSTANCE.forLanguage(original.getLanguage()).getFileNodeType();
            data = ((IStubFileElementType)originalType).getBuilder().buildStubTree(psi);
          }
        }
        finally {
          psi.putUserData(StubTreeLoader.FILE_TEXT_CONTENT_KEY, null);
        }
      }

      inputData.putUserData(stubElementKey, data);
      return data;
    }
  }
}
