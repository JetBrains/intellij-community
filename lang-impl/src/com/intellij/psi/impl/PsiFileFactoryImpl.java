/*
 * @author max
 */
package com.intellij.psi.impl;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageDialect;
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

  public PsiFile createFileFromText(@NotNull String name, @NotNull Language language, @NotNull String text) {
    return createFileFromText(name, language, text, true, true);
  }

  public PsiFile createFileFromText(@NotNull String name, @NotNull Language language, @NotNull String text, boolean physical,
                                    final boolean markAsCopy) {
    return trySetupPsiForFile(true, new LightVirtualFile(name, language, text), language, physical, markAsCopy);
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
      final PsiFile file = trySetupPsiForFile(false, virtualFile, ((LanguageFileType)fileType).getLanguage(), physical, markAsCopy);
      if (file != null) return file;
    }
    final SingleRootFileViewProvider singleRootFileViewProvider =
      new SingleRootFileViewProvider(myManager, virtualFile, physical);
    final PsiPlainTextFileImpl plainTextFile = new PsiPlainTextFileImpl(singleRootFileViewProvider);
    if(markAsCopy) CodeEditUtil.setNodeGenerated(plainTextFile.getNode(), true);
    return plainTextFile;
  }

  private PsiFile trySetupPsiForFile(final boolean useLanguage, final LightVirtualFile virtualFile, Language language,
                                     final boolean physical, final boolean markAsCopy) {
    FileViewProvider viewProvider = null;
    if (!useLanguage) {
      final FileViewProviderFactory factory = LanguageFileViewProviders.INSTANCE.forLanguage(language);
      viewProvider = factory != null? factory.createFileViewProvider(virtualFile, language, myManager, physical) : null;
    }
    if (viewProvider == null) viewProvider = new SingleRootFileViewProvider(myManager, virtualFile, physical);

    language = viewProvider.getBaseLanguage();
    final ParserDefinition parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(language);
    if (parserDefinition != null) {
      final PsiFile psiFile;
      if (useLanguage) {
        psiFile = parserDefinition.createFile(viewProvider);
        ((SingleRootFileViewProvider)viewProvider).forceCachedPsi(psiFile);
      }
      else {
        psiFile = viewProvider.getPsi(language);
      }
      if (psiFile != null) {
        if (language instanceof LanguageDialect) {
          psiFile.putUserData(PsiManagerImpl.LANGUAGE_DIALECT, (LanguageDialect)language);
        }
        if (markAsCopy) {
          final TreeElement node = (TreeElement)psiFile.getNode();
          assert node != null;
          node.acceptTree(new GeneratedMarkerVisitor());
        }
        return psiFile;
      }
    }
    else if (useLanguage) {
      throw new AssertionError("parser definition not found for "+language);
    }
    return null;
  }

  @NotNull
  public PsiFile createFileFromText(@NotNull String name,
                                    @NotNull FileType fileType, final Language language, @NotNull Language targetLanguage,
                                    LanguageDialect dialect, @NotNull CharSequence text,
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
        if (dialect != null) {
          psiFile.putUserData(PsiManagerImpl.LANGUAGE_DIALECT, dialect);
        }
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
      throw new RuntimeException("Cannot create binary files from text");
    }

    return createFileFromText(name, type, text);
  }

  public PsiFile createFileFromText(FileType fileType, final String fileName, CharSequence chars, int startOffset, int endOffset) {
    LOG.assertTrue(!fileType.isBinary());
    final CharSequence text = startOffset == 0 && endOffset == chars.length()?chars:new CharSequenceSubSequence(chars, startOffset, endOffset);
    return createFileFromText(fileName, fileType, text);
  }

}
