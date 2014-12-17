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
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Trinity;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiFile;
import com.intellij.psi.StubBuilder;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.IStubFileElementType;
import com.intellij.util.Function;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.FileContent;
import com.intellij.util.indexing.FileContentImpl;
import com.intellij.util.indexing.IndexingDataKeys;
import com.intellij.util.indexing.SubstitutedFileType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class StubTreeBuilder {
  private static final Key<Stub> stubElementKey = Key.create("stub.tree.for.file.content");

  private StubTreeBuilder() { }

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
      else {
        final LanguageFileType languageFileType = (LanguageFileType)fileType;
        Language l = languageFileType.getLanguage();
        final IFileElementType type = LanguageParserDefinitions.INSTANCE.forLanguage(l).getFileNodeType();

        CharSequence contentAsText = inputData.getContentAsText();
        FileContentImpl fileContent = (FileContentImpl)inputData;
        PsiFile psi = fileContent.getPsiFileForPsiDependentIndex();
        final FileViewProvider viewProvider = psi.getViewProvider();
        psi = viewProvider.getStubBindingRoot();
        psi.putUserData(IndexingDataKeys.FILE_TEXT_CONTENT_KEY, contentAsText);

        // if we load AST, it should be easily gc-able. See PsiFileImpl.createTreeElementPointer()
        psi.getManager().startBatchFilesProcessingMode();

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
            final StubBuilder stubBuilder = stubFileElementType.getBuilder();
            if (stubBuilder instanceof LightStubBuilder) {
              LightStubBuilder.FORCED_AST.set(fileContent.getLighterASTForPsiDependentIndex());
            }
            data = stubBuilder.buildStubTree(psi);

            final List<Pair<IStubFileElementType, PsiFile>> stubbedRoots = getStubbedRoots(viewProvider);
            if (stubbedRoots.size() > 1) {
              final List<PsiFileStub> stubs = new ArrayList<PsiFileStub>(stubbedRoots.size());
              stubs.add((PsiFileStub)data);

              for (Pair<IStubFileElementType, PsiFile> stubbedRoot : stubbedRoots) {
                if (psi == stubbedRoot.second) continue;
                final StubElement element = stubbedRoot.first.getBuilder().buildStubTree(stubbedRoot.second);
                if (element instanceof PsiFileStub) {
                  stubs.add((PsiFileStub)element);
                }
              }
              final PsiFileStub[] stubsArray = stubs.toArray(new PsiFileStub[stubs.size()]);
              for (PsiFileStub stub : stubsArray) {
                if (stub instanceof PsiFileStubImpl) {
                  ((PsiFileStubImpl)stub).setStubRoots(stubsArray);
                }
              }
            }
          }
        }
        finally {
          psi.putUserData(IndexingDataKeys.FILE_TEXT_CONTENT_KEY, null);
          psi.getManager().finishBatchFilesProcessingMode();
        }
      }

      inputData.putUserData(stubElementKey, data);
      return data;
    }
  }

  /** Order is deterministic. First element matches {@link FileViewProvider#getStubBindingRoot()} */
  @NotNull
  public static List<Pair<IStubFileElementType, PsiFile>> getStubbedRoots(@NotNull FileViewProvider viewProvider) {
    final List<Trinity<Language, IStubFileElementType, PsiFile>> roots =
      new SmartList<Trinity<Language, IStubFileElementType, PsiFile>>();
    final PsiFile stubBindingRoot = viewProvider.getStubBindingRoot();
    for (Language language : viewProvider.getLanguages()) {
      final PsiFile file = viewProvider.getPsi(language);
      if (file instanceof PsiFileImpl) {
        final IElementType contentType = ((PsiFileImpl)file).getContentElementType();
        if (contentType instanceof IStubFileElementType) {
          roots.add(Trinity.create(language, (IStubFileElementType)contentType, file));
        }
      }
    }

    ContainerUtil.sort(roots, new Comparator<Trinity<Language, IStubFileElementType, PsiFile>>() {
      @Override
      public int compare(Trinity<Language, IStubFileElementType, PsiFile> o1, Trinity<Language, IStubFileElementType, PsiFile> o2) {
        if (o1.third == stubBindingRoot) return o2.third == stubBindingRoot ? 0 : -1;
        else if (o2.third == stubBindingRoot) return 1;
        else return StringUtil.compare(o1.first.getID(), o2.first.getID(), false);
      }
    });

    return ContainerUtil.map(roots, new Function<Trinity<Language, IStubFileElementType, PsiFile>, Pair<IStubFileElementType, PsiFile>>() {
      @Override
      public Pair<IStubFileElementType, PsiFile> fun(Trinity<Language, IStubFileElementType, PsiFile> trinity) {
        return Pair.create(trinity.second, trinity.third);
      }
    });
  }
}
