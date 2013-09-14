/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.util.indexing.IndexingDataKeys;
import com.intellij.util.indexing.SubstitutedFileType;
import org.jetbrains.annotations.Nullable;

public class StubTreeBuilder {
  private static final Key<Stub> stubElementKey = Key.create("stub.tree.for.file.content");

  private StubTreeBuilder() {
  }

  @Nullable
  public static Stub buildStubTree(final FileContent inputData) {
    Stub data = inputData.getUserData(stubElementKey);
    if (data != null) return data;

    //noinspection SynchronizationOnLocalVariableOrMethodParameter
    synchronized (inputData) {
      data = inputData.getUserData(stubElementKey);
      if (data != null) return data;

      final FileType fileType = inputData.getFileType();

      final BinaryFileStubBuilder builder = BinaryFileStubBuilders.INSTANCE.forFileType(fileType);
      if (builder != null) {
        data = builder.buildStubTree(inputData);
      }
      if (data == null && !fileType.isBinary()) {
        final LanguageFileType languageFileType = (LanguageFileType)fileType;
        Language l = languageFileType.getLanguage();
        final IFileElementType type = LanguageParserDefinitions.INSTANCE.forLanguage(l).getFileNodeType();

        PsiFile psi = inputData.getPsiFile();
        psi = psi.getViewProvider().getStubBindingRoot();
        CharSequence contentAsText = inputData.getContentAsText();
        psi.putUserData(IndexingDataKeys.FILE_TEXT_CONTENT_KEY, contentAsText);

        try {
          IStubFileElementType stubFileElementType;
          if (type instanceof IStubFileElementType) {
            stubFileElementType = (IStubFileElementType)type;
          }
          else if (languageFileType instanceof SubstitutedFileType) {
            SubstitutedFileType substituted = (SubstitutedFileType)languageFileType;
            LanguageFileType original = (LanguageFileType)substituted.getOriginalFileType();
            final IFileElementType originalType = LanguageParserDefinitions.INSTANCE.forLanguage(original.getLanguage()).getFileNodeType();
            stubFileElementType = originalType instanceof IStubFileElementType ? (IStubFileElementType)originalType : null;
          }
          else {
            stubFileElementType = null;
          }
          if (stubFileElementType != null) {
            data = stubFileElementType.getBuilder().buildStubTree(psi);
          }
        }
        finally {
          psi.putUserData(IndexingDataKeys.FILE_TEXT_CONTENT_KEY, null);
        }
      }

      inputData.putUserData(stubElementKey, data);
      return data;
    }
  }
}
