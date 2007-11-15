/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
package com.intellij.lang;

import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.lang.findUsages.EmptyFindUsagesProvider;
import com.intellij.lang.findUsages.FindUsagesProvider;
import com.intellij.lang.folding.FoldingBuilder;
import com.intellij.lang.parameterInfo.ParameterInfoHandler;
import com.intellij.lang.surroundWith.SurroundDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * The base class for all programming language support implementations. Specific language implementations should inherit from this class
 * and its register instance wrapped with {@link com.intellij.openapi.fileTypes.LanguageFileType} instance through
 * <code>FileTypeManager.getInstance().registerFileType</code>
 * There should be exactly one instance of each Language. It is usually created when creating {@link com.intellij.openapi.fileTypes.LanguageFileType} and can be retrieved later
 * with {@link #findInstance(Class)}.
 * For the list of standard languages, see {@link com.intellij.lang.StdLanguages}.
 */
public abstract class Language {
  private static final Logger LOG = Logger.getInstance("#com.intellij.lang.Language");

  private static final SurroundDescriptor[] EMPTY_SURROUND_DESCRIPTORS_ARRAY = new SurroundDescriptor[0];
  private static final Map<Class<? extends Language>, Language> ourRegisteredLanguages = new HashMap<Class<? extends Language>, Language>();
  private final String myID;
  private final String[] myMimeTypes;
  public static final Language ANY = new Language("", "") { };
  private static final EmptyFindUsagesProvider EMPTY_FIND_USAGES_PROVIDER = new EmptyFindUsagesProvider();

  private FileType myFileType;

  protected Language(@NonNls String id) {
    this(id, "");
  }

  protected Language(@NonNls final String ID, @NonNls final String... mimeTypes) {
    myID = ID;
    myMimeTypes = mimeTypes;
    Class<? extends Language> langClass = getClass();

    if (ourRegisteredLanguages.containsKey(langClass)) {
      LOG.error("Language '" + langClass.getName() + "' is already registered");
      return;
    }
    ourRegisteredLanguages.put(langClass, this);
  }

  /**
   * @return collection of all languages registered so far.
   */
  public static Collection<Language> getRegisteredLanguages() {
    return Collections.unmodifiableCollection(ourRegisteredLanguages.values());
  }

  /**
   * @param klass <code>java.lang.Class</code> of the particular language. Serves key purpose.
   * @return instance of the <code>klass</code> language registered if any.
   */
  public static <T extends Language> T findInstance(Class<T> klass) {
    //noinspection unchecked
    return (T)ourRegisteredLanguages.get(klass);
  }


  /**
   * Override this method to provide parser implementation.
   * Parsed tree (AST) and program structure interface (PSI) based on AST is necessary for most of IDEA smart functions like
   * in-editor error highlighting, advanced syntax highlighting, error-checking, intention actions, inspections, folding,
   * finding usages, refactoring, file structure view etc.
   *
   * @return <code>ParserDefinition</code> interface implementation for this particular language or <code>null</code>
   *         if no parsing capabilities provided.
   */
  @Nullable
  public ParserDefinition getParserDefinition() {
    return null;
  }

  /**
   * Override this method to provide code folding capabilities when editing files of this language.
   * Please note {@link #getParserDefinition()} should return parser implementation for folding building to work properly.
   *
   * @return <code>FoldingBuilder</code> interface implementation for this particular language or <code>null</code>
   *         if no folding capabilities provided.
   */
  @Nullable
  public FoldingBuilder getFoldingBuilder() {
    return null;
  }

  /**
   * Override this method to provide paired brace matching and highlighting ability for editors of the language.
   * For this functionality to work properly own {@link SyntaxHighlighter} implementation is necessary.
   *
   * @return <code>PairedBraceMatcher</code> interface implementation for this particular language or <code>null</code>
   *         if no brace matching capabilities provided.
   */
  @Nullable
  public PairedBraceMatcher getPairedBraceMatcher() {
    return null;
  }

  /**
   * Override this method to provide comment-by-block and/or comment-by-line actions implementations for your language.
   * For this functionality to work properly {@link ParserDefinition} implementation is necessary.
   *
   * @return <code>Commenter</code> interface implementation for this particular language or <code>null</code>
   *         if no auto-commenting capabilities provided.
   */
  @Nullable
  public Commenter getCommenter() {
    return null;
  }

  /**
   * Word completion feature related method. It supposed to return token types of the places where word completion should be enabled.
   * Default implementation delegates to parser definition and returns comment tokens so words completion is enabled in comments
   * if parser definition is implemented.
   *
   * @return set of token types where word completion should be enabled.
   */
  @NotNull
  public TokenSet getReadableTextContainerElements() {
    final ParserDefinition parserDefinition = getParserDefinition();
    if (parserDefinition != null) return parserDefinition.getCommentTokens();
    return TokenSet.EMPTY;
  }

  /**
   * Override this method to provide find usages capability for the elements of your language
   * For this functionality to work properly {@link ParserDefinition} implementation is necessary.
   * <p/>
   * Default implementation returns mock find usages provider uncapable to search anything.
   *
   * @return <code>FindUsagesProvider</code> interface implementation for this particular language.
   */
  @NotNull
  public FindUsagesProvider getFindUsagesProvider() {
    return EMPTY_FIND_USAGES_PROVIDER;
  }

  /**
   * Override this method to provide structure view and file structure popup content for the files of your language.
   *
   * @param psiFile
   * @return <code>StructureViewBuilder</code> interface implementation for this particular language or <code>null</code>
   *         if no file structure implementation.
   */
  @Nullable
  public StructureViewBuilder getStructureViewBuilder(PsiFile psiFile) {
    return null;
  }

  /**
   * Override this method to provide 'surround with...' feature implementation for editors of the files in your language.
   * <p/>
   * Default implementation returns empty array of SurroundDescriptor implementations thus disabling the feature.
   *
   * @return <code>SurroundDescriptor</code> interface implementations for this particular language.
   */
  @NotNull
  public SurroundDescriptor[] getSurroundDescriptors() {
    return EMPTY_SURROUND_DESCRIPTORS_ARRAY;
  }

  public String toString() {
    //noinspection HardCodedStringLiteral
    return "Language: " + myID;
  }

  /**
   * Returns the list of MIME types corresponding to the language. The language MIME type is used for specifying the base language
   * of a JSP page.
   *
   * @return The list of MIME types.
   */
  public String[] getMimeTypes() {
    return myMimeTypes;
  }

  /**
   * Returns a user-readable name of the language.
   *
   * @return the name of the language.
   */
  @NotNull
  public String getID() {
    return myID;
  }

  @Nullable
  public FileType getAssociatedFileType() {
    return myFileType;
  }

  public void associateFileType(FileType type) {
    myFileType = type;
  }

  @Nullable
  public FileViewProvider createViewProvider(final VirtualFile file, final PsiManager manager, final boolean physical) {
    return null;
  }

  @Nullable
  public LanguageDialect[] getAvailableLanguageDialects() {
    return null;
  }

  @Nullable
  public ParameterInfoHandler[] getParameterInfoHandlers() {
    return null;
  }

  @Nullable
  public LanguageCodeInsightActionHandler getGotoSuperHandler() {
    return null;
  }
}
