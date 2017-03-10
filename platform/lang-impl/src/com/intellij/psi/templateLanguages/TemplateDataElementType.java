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
package com.intellij.psi.templateLanguages;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageExtension;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lexer.Lexer;
import com.intellij.lexer.MergingLexerAdapter;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.SingleRootFileViewProvider;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.CharTable;
import com.intellij.util.LocalTimeCounter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author peter
 */
public class TemplateDataElementType extends IFileElementType implements ITemplateDataElementType {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.templateLanguages.TemplateDataElementType");

  public static final LanguageExtension<TreePatcher> TREE_PATCHER = new LanguageExtension<>("com.intellij.lang.treePatcher", new SimpleTreePatcher());

  @NotNull private final IElementType myTemplateElementType;
  @NotNull private final IElementType myOuterElementType;

  public TemplateDataElementType(@NonNls String debugName, Language language, @NotNull IElementType templateElementType, @NotNull IElementType outerElementType) {
    super(debugName, language);
    myTemplateElementType = templateElementType;
    myOuterElementType = outerElementType;
  }

  protected Lexer createBaseLexer(TemplateLanguageFileViewProvider viewProvider) {
    return LanguageParserDefinitions.INSTANCE.forLanguage(viewProvider.getBaseLanguage()).createLexer(viewProvider.getManager().getProject());
  }

  protected LanguageFileType createTemplateFakeFileType(final Language language) {
    return new TemplateFileType(language);
  }

  @Override
  public ASTNode parseContents(ASTNode chameleon) {
    final CharTable charTable = SharedImplUtil.findCharTableByTree(chameleon);
    final FileElement fileElement = TreeUtil.getFileElement((TreeElement)chameleon);
    final PsiFile psiFile = (PsiFile)fileElement.getPsi();
    PsiFile originalPsiFile = psiFile.getOriginalFile();

    final TemplateLanguageFileViewProvider viewProvider = (TemplateLanguageFileViewProvider)originalPsiFile.getViewProvider();

    final Language templateLanguage = getTemplateFileLanguage(viewProvider);
    final CharSequence sourceCode = chameleon.getChars();

    final PsiFile templatePsiFile = createTemplateFile(psiFile, templateLanguage, sourceCode, viewProvider);

    final FileElement templateFileElement = ((PsiFileImpl)templatePsiFile).calcTreeElement();

    DebugUtil.startPsiModification("template language parsing");
    try {
      prepareParsedTemplateFile(templateFileElement);
      Lexer templateLexer = LanguageParserDefinitions.INSTANCE.forLanguage(templateLanguage).createLexer(psiFile.getProject());
      final Lexer blackAndWhiteLexer = new MergingLexerAdapter(
        new TemplateBlackAndWhiteLexer(createBaseLexer(viewProvider), templateLexer, myTemplateElementType, myOuterElementType),
        TokenSet.create(myTemplateElementType, myOuterElementType));
      blackAndWhiteLexer.start(sourceCode);
      insertOuters(templateFileElement, blackAndWhiteLexer, charTable);

      TreeElement childNode = templateFileElement.getFirstChildNode();

      DebugUtil.checkTreeStructure(templateFileElement);
      DebugUtil.checkTreeStructure(chameleon);
      if (fileElement != chameleon) {
        DebugUtil.checkTreeStructure(psiFile.getNode());
        DebugUtil.checkTreeStructure(originalPsiFile.getNode());
      }

      return childNode;
    }
    finally {
      DebugUtil.finishPsiModification();
    }
  }

  protected void prepareParsedTemplateFile(@NotNull FileElement root) {
  }

  protected Language getTemplateFileLanguage(TemplateLanguageFileViewProvider viewProvider) {
    return viewProvider.getTemplateDataLanguage();
  }

  protected PsiFile createTemplateFile(final PsiFile psiFile,
                                     final Language templateLanguage,
                                     final CharSequence sourceCode,
                                     final TemplateLanguageFileViewProvider viewProvider) {
    final Lexer baseLexer = createBaseLexer(viewProvider);
    final CharSequence templateSourceCode = createTemplateText(sourceCode, baseLexer);
    return createPsiFileFromSource(templateLanguage, templateSourceCode, psiFile.getManager());
  }

  protected CharSequence createTemplateText(CharSequence buf, Lexer lexer) {
    StringBuilder result = new StringBuilder(buf.length());
    lexer.start(buf);

    while (lexer.getTokenType() != null) {
      if (lexer.getTokenType() == myTemplateElementType) {
        appendCurrentTemplateToken(result, buf, lexer);
      }
      lexer.advance();
    }

    return result;
  }

  protected void appendCurrentTemplateToken(StringBuilder result, CharSequence buf, Lexer lexer) {
    result.append(buf, lexer.getTokenStart(), lexer.getTokenEnd());
  }

  private void insertOuters(TreeElement templateFileElement, Lexer blackAndWhiteLexer, final CharTable charTable) {
    TreePatcher templateTreePatcher = TREE_PATCHER.forLanguage(templateFileElement.getPsi().getLanguage());

    int treeOffset = 0;
    LeafElement currentLeaf = TreeUtil.findFirstLeaf(templateFileElement);
    while (blackAndWhiteLexer.getTokenType() != null) {
      IElementType tokenType = blackAndWhiteLexer.getTokenType();
      if (tokenType != myTemplateElementType) {
        while (currentLeaf != null && treeOffset < blackAndWhiteLexer.getTokenStart()) {
          treeOffset += currentLeaf.getTextLength();
          int currentTokenStart = blackAndWhiteLexer.getTokenStart();
          if (treeOffset > currentTokenStart) {
            currentLeaf = templateTreePatcher.split(currentLeaf, currentLeaf.getTextLength() - (treeOffset - currentTokenStart), charTable);
            treeOffset = currentTokenStart;
          }
          currentLeaf = (LeafElement)TreeUtil.nextLeaf(currentLeaf);
        }

        if (currentLeaf == null) break;

        final OuterLanguageElementImpl newLeaf = createOuterLanguageElement(blackAndWhiteLexer, charTable, myOuterElementType);
        templateTreePatcher.insert(currentLeaf.getTreeParent(), currentLeaf, newLeaf);
        currentLeaf.getTreeParent().subtreeChanged();
        currentLeaf = newLeaf;
      }
      blackAndWhiteLexer.advance();
    }

    if (blackAndWhiteLexer.getTokenType() != null) {
      assert blackAndWhiteLexer.getTokenType() != myTemplateElementType;
      final OuterLanguageElementImpl newLeaf = createOuterLanguageElement(blackAndWhiteLexer, charTable, myOuterElementType);
      ((CompositeElement)templateFileElement).rawAddChildren(newLeaf);
      ((CompositeElement)templateFileElement).subtreeChanged();
    }
  }

  protected OuterLanguageElementImpl createOuterLanguageElement(final Lexer lexer, final CharTable table,
                                                                @NotNull IElementType outerElementType) {
    final CharSequence buffer = lexer.getBufferSequence();
    final int tokenStart = lexer.getTokenStart();
    if (tokenStart < 0 || tokenStart > buffer.length()) {
      LOG.error("Invalid start: " + tokenStart + "; " + lexer);
    }
    final int tokenEnd = lexer.getTokenEnd();
    if (tokenEnd < 0 || tokenEnd > buffer.length() || tokenEnd < tokenStart) {
      LOG.error("Invalid range: (" + tokenStart+","+tokenEnd + "); buffer length:"+buffer.length()+"; " + lexer);
    }

    return new OuterLanguageElementImpl(outerElementType, table.intern(buffer, tokenStart, tokenEnd));
  }

  protected PsiFile createPsiFileFromSource(final Language language, CharSequence sourceCode, PsiManager manager) {
    @NonNls
    final LightVirtualFile virtualFile = new LightVirtualFile("foo", createTemplateFakeFileType(language), sourceCode, LocalTimeCounter.currentTime());

    FileViewProvider viewProvider = new SingleRootFileViewProvider(manager, virtualFile, false) {
      @Override
      @NotNull
      public Language getBaseLanguage() {
        return language;
      }
    };

    // Since we're already inside a template language PSI that was built regardless of the file size (for whatever reason), 
    // there should also be no file size checks for template data files.
    SingleRootFileViewProvider.doNotCheckFileSizeLimit(virtualFile);

    return viewProvider.getPsi(language);
  }

  protected static class TemplateFileType extends LanguageFileType {
    private final Language myLanguage;

    public TemplateFileType(final Language language) {
      super(language);
      myLanguage = language;
    }

    @Override
    @NotNull
    public String getDefaultExtension() {
      return "";
    }

    @Override
    @NotNull
    @NonNls
    public String getDescription() {
      return "fake for language" + myLanguage.getID();
    }

    @Override
    @Nullable
    public Icon getIcon() {
      return null;
    }

    @Override
    @NotNull
    @NonNls
    public String getName() {
      return myLanguage.getID();
    }

  }
}
