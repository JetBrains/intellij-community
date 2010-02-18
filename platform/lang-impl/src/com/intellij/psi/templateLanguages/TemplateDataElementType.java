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
import com.intellij.psi.impl.source.DummyHolder;
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

  private final static LanguageExtension<TreePatcher> TREE_PATCHER = new LanguageExtension<TreePatcher>("com.intellij.lang.treePatcher", new SimpleTreePatcher()); 

  private final IElementType myTemplateElementType;
  private final IElementType myOuterElementType;

  public TemplateDataElementType(@NonNls String debugName, Language language, IElementType templateElementType, IElementType outerElementType) {
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

  public ASTNode parseContents(ASTNode chameleon) {
    final CharTable table = SharedImplUtil.findCharTableByTree(chameleon);
    final FileElement treeElement = new DummyHolder(((TreeElement)chameleon).getManager(), null, table).getTreeElement();
    final PsiFile file = (PsiFile)TreeUtil.getFileElement((TreeElement)chameleon).getPsi();
    PsiFile originalFile = file.getOriginalFile();

    final TemplateLanguageFileViewProvider viewProvider = (TemplateLanguageFileViewProvider)originalFile.getViewProvider();

    final Language language = viewProvider.getTemplateDataLanguage();
    final CharSequence chars = chameleon.getChars();

    final Lexer baseLexer = createBaseLexer(viewProvider);
    final CharSequence templateText = createTemplateText(chars, baseLexer);
    final PsiFile templateFile = createFromText(language, templateText, file.getManager());

    final TreeElement parsed = ((PsiFileImpl)templateFile).calcTreeElement();
    Lexer langLexer = LanguageParserDefinitions.INSTANCE.forLanguage(language).createLexer(file.getProject());
    final Lexer lexer = new MergingLexerAdapter(
      new TemplateBlackAndWhiteLexer(createBaseLexer(viewProvider), langLexer, myTemplateElementType, myOuterElementType),
      TokenSet.create(myTemplateElementType, myOuterElementType));
    lexer.start(chars);
    insertOuters(parsed, lexer, table);

    if (parsed != null) {
      final TreeElement element = parsed.getFirstChildNode();
      if (element != null) {
        treeElement.rawAddChildren(element);
      }
    }

    treeElement.clearCaches();
    treeElement.subtreeChanged();
    return treeElement.getFirstChildNode();
  }

  private CharSequence createTemplateText(CharSequence buf, Lexer lexer) {
    StringBuilder result = new StringBuilder(buf.length());
    lexer.start(buf);

    while (lexer.getTokenType() != null) {
      if (lexer.getTokenType() == myTemplateElementType) {
        result.append(buf, lexer.getTokenStart(), lexer.getTokenEnd());
      }
      lexer.advance();
    }

    return result;
  }

  private void insertOuters(TreeElement root, Lexer lexer, final CharTable table) {
    TreePatcher patcher = TREE_PATCHER.forLanguage(root.getPsi().getLanguage());

    int treeOffset = 0;
    LeafElement leaf = TreeUtil.findFirstLeaf(root);
    while (lexer.getTokenType() != null) {
      IElementType tt = lexer.getTokenType();
      if (tt != myTemplateElementType) {
        while (leaf != null && treeOffset < lexer.getTokenStart()) {
          treeOffset += leaf.getTextLength();
          if (treeOffset > lexer.getTokenStart()) {
            leaf = patcher.split(leaf, leaf.getTextLength() - (treeOffset - lexer.getTokenStart()), table);
            treeOffset = lexer.getTokenStart();
          }
          leaf = (LeafElement)TreeUtil.nextLeaf(leaf);
        }

        if (leaf == null) break;

        final OuterLanguageElementImpl newLeaf = createOuterLanguageElement(lexer, table, myOuterElementType);
        patcher.insert(leaf.getTreeParent(), leaf, newLeaf);
        leaf.getTreeParent().subtreeChanged();
        leaf = newLeaf;
      }
      lexer.advance();
    }

    if (lexer.getTokenType() != null) {
      assert lexer.getTokenType() != myTemplateElementType;
      final OuterLanguageElementImpl newLeaf = createOuterLanguageElement(lexer, table, myOuterElementType);
      ((CompositeElement)root).rawAddChildren(newLeaf);
      ((CompositeElement)root).subtreeChanged();
    }
  }

  protected OuterLanguageElementImpl createOuterLanguageElement(final Lexer lexer, final CharTable table,
                                                                final IElementType outerElementType) {
    final CharSequence buffer = lexer.getBufferSequence();
    final int tokenStart = lexer.getTokenStart();
    if (tokenStart < 0 || tokenStart > buffer.length()) {
      LOG.error("Invalid start: " + tokenStart + "; " + lexer);
    }
    final int tokenEnd = lexer.getTokenEnd();
    if (tokenEnd < 0 || tokenEnd > buffer.length()) {
      LOG.error("Invalid end: " + tokenEnd + "; " + lexer);
    }

    return new OuterLanguageElementImpl(outerElementType, table.intern(buffer, tokenStart, tokenEnd));
  }

  private PsiFile createFromText(final Language language, CharSequence text, PsiManager manager) {
    @NonNls
    final LightVirtualFile virtualFile = new LightVirtualFile("foo", createTemplateFakeFileType(language), text, LocalTimeCounter.currentTime());

    FileViewProvider viewProvider = new SingleRootFileViewProvider(manager, virtualFile, false) {
      @NotNull
      public Language getBaseLanguage() {
        return language;
      }
    };

    return viewProvider.getPsi(language);
  }

  protected static class TemplateFileType extends LanguageFileType {
    private final Language myLanguage;

    public TemplateFileType(final Language language) {
      super(language);
      myLanguage = language;
    }

    @NotNull
    public String getDefaultExtension() {
      return "";
    }

    @NotNull
    @NonNls
    public String getDescription() {
      return "fake for language" + myLanguage.getID();
    }

    @Nullable
    public Icon getIcon() {
      return null;
    }

    @NotNull
    @NonNls
    public String getName() {
      return myLanguage.getID();
    }

  }
}
