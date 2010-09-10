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
package com.intellij.psi.impl;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.ParserDefinition;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiPlainTextFileImpl;
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.LocalTimeCounter;
import com.intellij.util.text.CharSequenceSubSequence;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PsiFileFactoryImpl extends PsiFileFactory {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.PsiFileFactoryImpl");
  private final PsiManager myManager;

  public PsiFileFactoryImpl(final PsiManager manager) {
    myManager = manager;
  }

  @NotNull
  public PsiFile createFileFromText(@NotNull String name, @NotNull FileType fileType, @NotNull CharSequence text,
                                    long modificationStamp, final boolean physical) {
    return createFileFromText(name, fileType, text, modificationStamp, physical, true);
  }

  public PsiFile createFileFromText(@NotNull String name, @NotNull Language language, @NotNull CharSequence text) {
    return createFileFromText(name, language, text, true, true);
  }

  public PsiFile createFileFromText(@NotNull String name, @NotNull Language language, @NotNull CharSequence text, boolean physical,
                                    final boolean markAsCopy) {
    return createFileFromText(name, language, text, physical, markAsCopy, false);
  }

  @Override
  public PsiFile createFileFromText(@NotNull String name,
                                    @NotNull Language language,
                                    @NotNull CharSequence text,
                                    boolean physical,
                                    boolean markAsCopy,
                                    boolean noSizeLimit) {
    LightVirtualFile virtualFile = new LightVirtualFile(name, language, text);
    if (noSizeLimit) virtualFile.putUserData(SingleRootFileViewProvider.ourNoSizeLimitKey, Boolean.TRUE);
    return trySetupPsiForFile(virtualFile, language, physical, markAsCopy);
  }

  @NotNull
  public PsiFile createFileFromText(@NotNull String name,
                                    @NotNull FileType fileType,
                                    @NotNull CharSequence text,
                                    long modificationStamp,
                                    final boolean physical,
                                    boolean markAsCopy) {
    final LightVirtualFile virtualFile = new LightVirtualFile(name, fileType, text, modificationStamp);
    if(fileType instanceof LanguageFileType){
      final Language language =
          LanguageSubstitutors.INSTANCE.substituteLanguage(((LanguageFileType)fileType).getLanguage(), virtualFile, myManager.getProject());
      final PsiFile file = trySetupPsiForFile(virtualFile, language, physical, markAsCopy);
      if (file != null) return file;
    }
    final SingleRootFileViewProvider singleRootFileViewProvider =
      new SingleRootFileViewProvider(myManager, virtualFile, physical);
    final PsiPlainTextFileImpl plainTextFile = new PsiPlainTextFileImpl(singleRootFileViewProvider);
    if(markAsCopy) CodeEditUtil.setNodeGenerated(plainTextFile.getNode(), true);
    return plainTextFile;
  }

  @Nullable
  public PsiFile trySetupPsiForFile(final LightVirtualFile virtualFile, Language language,
                                    final boolean physical, final boolean markAsCopy) {
    final FileViewProviderFactory factory = LanguageFileViewProviders.INSTANCE.forLanguage(language);
    FileViewProvider viewProvider = factory != null ? factory.createFileViewProvider(virtualFile, language, myManager, physical) : null;
    if (viewProvider == null) viewProvider = new SingleRootFileViewProvider(myManager, virtualFile, physical);

    language = viewProvider.getBaseLanguage();
    final ParserDefinition parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(language);
    if (parserDefinition != null) {
      final PsiFile psiFile = viewProvider.getPsi(language);
      if (psiFile != null) {
        if (markAsCopy) {
          final TreeElement node = (TreeElement)psiFile.getNode();
          assert node != null;
          node.acceptTree(new GeneratedMarkerVisitor());
        }
        return psiFile;
      }
    }
    return null;
  }

  @NotNull
  public PsiFile createFileFromText(@NotNull String name,
                                    @NotNull FileType fileType, final Language language, @NotNull Language targetLanguage, @NotNull CharSequence text,
                                    long modificationStamp,
                                    final boolean physical,
                                    boolean markAsCopy) {
    final LightVirtualFile virtualFile = new LightVirtualFile(name, fileType, text, modificationStamp);

    final ParserDefinition parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(language);
    final FileViewProviderFactory factory = LanguageFileViewProviders.INSTANCE.forLanguage(language);
    FileViewProvider viewProvider = factory != null ? factory.createFileViewProvider(virtualFile, language, myManager, physical) : null;
    if (viewProvider == null) viewProvider = new SingleRootFileViewProvider(myManager, virtualFile, physical);

    if (parserDefinition != null){
      final PsiFile psiFile = viewProvider.getPsi(targetLanguage);
      if (psiFile != null) {
        if(markAsCopy) {
          final TreeElement node = (TreeElement)psiFile.getNode();
          assert node != null;
          node.acceptTree(new GeneratedMarkerVisitor());
        }
        return psiFile;
      }
    }

    final SingleRootFileViewProvider singleRootFileViewProvider =
        new SingleRootFileViewProvider(myManager, virtualFile, physical);
    final PsiPlainTextFileImpl plainTextFile = new PsiPlainTextFileImpl(singleRootFileViewProvider);
    if(markAsCopy) CodeEditUtil.setNodeGenerated(plainTextFile.getNode(), true);
    return plainTextFile;
  }

  @NotNull
  public PsiFile createFileFromText(@NotNull String name, @NotNull FileType fileType, @NotNull CharSequence text) {
    return createFileFromText(name, fileType, text, LocalTimeCounter.currentTime(), false);
  }

  @NotNull
  public PsiFile createFileFromText(@NotNull String name, @NotNull String text){
    FileTypeManager fileTypeManager = FileTypeManager.getInstance();
    FileType type = fileTypeManager.getFileTypeByFileName(name);
    if (type.isBinary()) {
      throw new RuntimeException("Cannot create binary files from text: name " + name + ", file type " + type);
    }

    return createFileFromText(name, type, text);
  }

  public PsiFile createFileFromText(FileType fileType, final String fileName, CharSequence chars, int startOffset, int endOffset) {
    LOG.assertTrue(!fileType.isBinary());
    final CharSequence text = startOffset == 0 && endOffset == chars.length()?chars:new CharSequenceSubSequence(chars, startOffset, endOffset);
    return createFileFromText(fileName, fileType, text);
  }

  @Nullable
  @Override
  public PsiFile createFileFromText(@NotNull CharSequence chars, @NotNull PsiFile original) {
    final PsiFile file = createFileFromText(original.getName(), original.getLanguage(), chars, false, true);
    if (file != null) {
      file.putUserData(ORIGINAL_FILE, original);
    }
    return file;
  }
}
