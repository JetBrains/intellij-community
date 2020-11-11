// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl;

import com.intellij.lang.*;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.DummyHolder;
import com.intellij.psi.impl.source.DummyHolderFactory;
import com.intellij.psi.impl.source.PsiPlainTextFileImpl;
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.serviceContainer.NonInjectable;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.LocalTimeCounter;
import com.intellij.util.text.CharSequenceSubSequence;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PsiFileFactoryImpl extends PsiFileFactory {
  private static final Logger LOG = Logger.getInstance(PsiFileFactoryImpl.class);
  private final PsiManager myManager;

  public PsiFileFactoryImpl(@NotNull Project project) {
    myManager = PsiManager.getInstance(project);
  }

  @NonInjectable
  public PsiFileFactoryImpl(@NotNull PsiManager manager) {
    myManager = manager;
  }

  @Override
  @NotNull
  public PsiFile createFileFromText(@NotNull String name, @NotNull FileType fileType, @NotNull CharSequence text,
                                    long modificationStamp, final boolean eventSystemEnabled) {
    return createFileFromText(name, fileType, text, modificationStamp, eventSystemEnabled, true);
  }

  @Override
  public PsiFile createFileFromText(@NotNull String name, @NotNull Language language, @NotNull CharSequence text) {
    return createFileFromText(name, language, text, true, true);
  }

  @Override
  public PsiFile createFileFromText(@NotNull String name, @NotNull Language language, @NotNull CharSequence text,
                                    boolean eventSystemEnabled, boolean markAsCopy) {
    return createFileFromText(name, language, text, eventSystemEnabled, markAsCopy, false);
  }

  @Override
  public PsiFile createFileFromText(@NotNull String name, @NotNull Language language, @NotNull CharSequence text,
                                    boolean eventSystemEnabled, boolean markAsCopy, boolean noSizeLimit) {
    return createFileFromText(name, language, text, eventSystemEnabled, markAsCopy, noSizeLimit, null);
  }

  @Override
  public PsiFile createFileFromText(@NotNull String name, @NotNull Language language, @NotNull CharSequence text,
                                    boolean eventSystemEnabled, boolean markAsCopy, boolean noSizeLimit,
                                    @Nullable VirtualFile original) {
    LightVirtualFile virtualFile = new LightVirtualFile(name, language, text);
    if (original != null) {
      virtualFile.setOriginalFile(original);
      virtualFile.setFileType(original.getFileType());
    }
    if (noSizeLimit) {
      SingleRootFileViewProvider.doNotCheckFileSizeLimit(virtualFile);
    }
    return trySetupPsiForFile(virtualFile, language, eventSystemEnabled, markAsCopy);
  }

  @Override
  @NotNull
  public PsiFile createFileFromText(@NotNull String name,
                                    @NotNull FileType fileType,
                                    @NotNull CharSequence text,
                                    long modificationStamp,
                                    final boolean eventSystemEnabled,
                                    boolean markAsCopy) {
    LightVirtualFile virtualFile = new LightVirtualFile(name, fileType, text, modificationStamp);
    Language language = LanguageUtil.getLanguageForPsi(myManager.getProject(), virtualFile, fileType);
    if (language != null) {
      PsiFile file = trySetupPsiForFile(virtualFile, language, eventSystemEnabled, markAsCopy);
      if (file != null) return file;
    }
    final SingleRootFileViewProvider singleRootFileViewProvider =
      new SingleRootFileViewProvider(myManager, virtualFile, eventSystemEnabled);
    final PsiPlainTextFileImpl plainTextFile = new PsiPlainTextFileImpl(singleRootFileViewProvider);
    if(markAsCopy) CodeEditUtil.setNodeGenerated(plainTextFile.getNode(), true);
    return plainTextFile;
  }

  @Nullable
  public PsiFile trySetupPsiForFile(@NotNull LightVirtualFile virtualFile,
                                    @NotNull Language language,
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
          if (psiFile.getNode() == null) {
            throw new AssertionError("No node for file " + psiFile + "; language=" + language);
          }
          markGenerated(psiFile);
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
          markGenerated(psiFile);
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

  @Override
  @NotNull
  public PsiFile createFileFromText(@NotNull String name, @NotNull FileType fileType, @NotNull CharSequence text) {
    return createFileFromText(name, fileType, text, LocalTimeCounter.currentTime(), false);
  }

  @Override
  @NotNull
  public PsiFile createFileFromText(@NotNull String name, @NotNull String text){
    FileType type = FileTypeRegistry.getInstance().getFileTypeByFileName(name);
    if (type.isBinary()) {
      throw new RuntimeException("Cannot create binary files from text: name " + name + ", file type " + type);
    }

    return createFileFromText(name, type, text);
  }

  @Override
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

  @Nullable
  public PsiElement createElementFromText(@Nullable final String text,
                                          @NotNull final Language language,
                                          @NotNull final IElementType type,
                                          @Nullable final PsiElement context) {
    if (text == null) return null;
    final DummyHolder result = DummyHolderFactory.createHolder(myManager, language, context);
    final FileElement holder = result.getTreeElement();

    final ParserDefinition parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(language);
    if (parserDefinition == null) {
      throw new AssertionError("No parser definition for " + language);
    }
    final Project project = myManager.getProject();
    final Lexer lexer = parserDefinition.createLexer(project);
    final PsiBuilder builder = PsiBuilderFactory.getInstance().createBuilder(project, holder, lexer, language, text);
    final ASTNode node = parserDefinition.createParser(project).parse(type, builder);
    holder.rawAddChildren((TreeElement)node);
    markGenerated(result);
    return node.getPsi();
  }


  public static void markGenerated(PsiElement element) {
    final TreeElement node = (TreeElement)element.getNode();
    assert node != null;
    node.acceptTree(new GeneratedMarkerVisitor());
  }
}
