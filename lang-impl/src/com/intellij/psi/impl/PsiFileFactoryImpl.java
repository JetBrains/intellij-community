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
import com.intellij.psi.impl.file.impl.FileManager;
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
    ParserDefinition parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(language);

    final LightVirtualFile virtualFile = new LightVirtualFile(name, language, text);
    final FileManager fileManager = ((PsiManagerEx)myManager).getFileManager();
    final SingleRootFileViewProvider viewProvider = (SingleRootFileViewProvider)fileManager.createFileViewProvider(virtualFile, true);
    assert parserDefinition != null;
    final PsiFile psiFile = parserDefinition.createFile(viewProvider);
    viewProvider.forceCachedPsi(psiFile);
    if (language instanceof LanguageDialect) {
      psiFile.putUserData(PsiManagerImpl.LANGUAGE_DIALECT, (LanguageDialect)language);
    }
    TreeElement node = (TreeElement)psiFile.getNode();
    assert node != null;
    node.acceptTree(new GeneratedMarkerVisitor());

    return psiFile;
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
      final Language language = ((LanguageFileType)fileType).getLanguage();
      final ParserDefinition parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(language);
      final FileViewProvider viewProvider = ((PsiManagerEx)myManager).getFileManager().createFileViewProvider(virtualFile, physical);

      if (parserDefinition != null){
        final PsiFile psiFile = viewProvider.getPsi(language);
        if (psiFile != null) {
          if (language instanceof LanguageDialect) {
            psiFile.putUserData(PsiManagerImpl.LANGUAGE_DIALECT, (LanguageDialect)language);
          }
          if(markAsCopy) {
            final TreeElement node = (TreeElement)psiFile.getNode();
            assert node != null;
            node.acceptTree(new GeneratedMarkerVisitor());
          }
          return psiFile;
        }
      }
    }
    final SingleRootFileViewProvider singleRootFileViewProvider =
      new SingleRootFileViewProvider(myManager, virtualFile, physical);
    final PsiPlainTextFileImpl plainTextFile = new PsiPlainTextFileImpl(singleRootFileViewProvider);
    if(markAsCopy) CodeEditUtil.setNodeGenerated(plainTextFile.getNode(), true);
    return plainTextFile;
  }

  @NotNull
  public PsiFile createFileFromText(@NotNull String name,
                                    @NotNull FileType fileType, final Language language, @NotNull Language targetLanguage,
                                    LanguageDialect dialect, @NotNull CharSequence text,
                                    long modificationStamp,
                                    final boolean physical,
                                    boolean markAsCopy) {
    final LightVirtualFile virtualFile = new LightVirtualFile(name, fileType, text, modificationStamp);

    if(fileType instanceof LanguageFileType){
      final ParserDefinition parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(language);
      final FileViewProvider viewProvider = ((PsiManagerEx)myManager).getFileManager().createFileViewProvider(virtualFile, physical);
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