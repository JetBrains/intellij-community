
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

package com.intellij.find.impl;

import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.codeInsight.highlighting.HighlightManagerImpl;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.find.*;
import com.intellij.find.findUsages.FindUsagesManager;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.ParserDefinition;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.ui.LightweightHint;
import com.intellij.ui.ReplacePromptDialog;
import com.intellij.usages.UsageViewManager;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.text.StringSearcher;
import gnu.trove.THashSet;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@State(
  name = "FindManager",
  storages = {
    @Storage(
      id ="other",
      file = "$WORKSPACE_FILE$"
    )}
)
public class FindManagerImpl extends FindManager implements PersistentStateComponent<Element> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.find.impl.FindManagerImpl");

  private final FindUsagesManager myFindUsagesManager;
  private boolean isFindWasPerformed = false;
  private Point myReplaceInFilePromptPos = new Point(-1, -1);
  private Point myReplaceInProjectPromptPos = new Point(-1, -1);
  private final FindModel myFindInProjectModel = new FindModel();
  private final FindModel myFindInFileModel = new FindModel();
  private FindModel myFindNextModel = null;
  private static final FindResultImpl NOT_FOUND_RESULT = new FindResultImpl();
  private final Project myProject;
  private final MessageBus myBus;
  private static final Key<Boolean> HIGHLIGHTER_WAS_NOT_FOUND_KEY = Key.create("com.intellij.find.impl.FindManagerImpl.HighlighterNotFoundKey");
  @NonNls private static final String FIND_USAGES_MANAGER_ELEMENT = "FindUsagesManager";
  public static final boolean ourHasSearchInCommentsAndLiterals = ApplicationManagerEx.getApplicationEx().isInternal(); // TODO: maxim
  private FindDialog myFindDialog;

  public FindManagerImpl(Project project, FindSettings findSettings, UsageViewManager anotherManager, MessageBus bus) {
    myProject = project;
    myBus = bus;
    findSettings.initModelBySetings(myFindInFileModel);
    findSettings.initModelBySetings(myFindInProjectModel);

    myFindInFileModel.setCaseSensitive(findSettings.isLocalCaseSensitive());
    myFindInFileModel.setWholeWordsOnly(findSettings.isLocalWholeWordsOnly());

    myFindUsagesManager = new FindUsagesManager(myProject, anotherManager);
    myFindInProjectModel.setMultipleFiles(true);
  }

  public Element getState() {
    Element element = new Element("FindManager");
    final Element findUsages = new Element(FIND_USAGES_MANAGER_ELEMENT);
    element.addContent(findUsages);
    try {
      myFindUsagesManager.writeExternal(findUsages);
    }
    catch (WriteExternalException e) {
      LOG.error(e);
    }
    return element;
  }

  public void loadState(final Element state) {
    final Element findUsages = state.getChild(FIND_USAGES_MANAGER_ELEMENT);
    if (findUsages != null) {
      try {
        myFindUsagesManager.readExternal(findUsages);
      }
      catch (InvalidDataException e) {
        LOG.error(e);
      }
    }
  }

  public int showPromptDialog(final FindModel model, String title) {
    ReplacePromptDialog replacePromptDialog = new ReplacePromptDialog(model.isMultipleFiles(), title, myProject) {
      @Nullable
      public Point getInitialLocation() {
        if (model.isMultipleFiles() && myReplaceInProjectPromptPos.x >= 0 && myReplaceInProjectPromptPos.y >= 0){
          return myReplaceInProjectPromptPos;
        }
        if (!model.isMultipleFiles() && myReplaceInFilePromptPos.x >= 0 && myReplaceInFilePromptPos.y >= 0){
          return myReplaceInFilePromptPos;
        }
        return null;
      }
    };

    replacePromptDialog.show();

    if (model.isMultipleFiles()){
      myReplaceInProjectPromptPos = replacePromptDialog.getLocation();
    }
    else{
      myReplaceInFilePromptPos = replacePromptDialog.getLocation();
    }
    return replacePromptDialog.getExitCode();
  }

  public void showFindDialog(@NotNull final FindModel model, @NotNull final Runnable okHandler) {
    if(myFindDialog==null || Disposer.isDisposed(myFindDialog.getDisposable())){
      myFindDialog = new FindDialog(myProject, model, new Runnable(){
        public void run() {
          String stringToFind = model.getStringToFind();
          if (stringToFind.length() == 0){
            return;
          }
          FindSettings.getInstance().addStringToFind(stringToFind);
          if (!model.isMultipleFiles()){
            setFindWasPerformed();
          }
          if (model.isReplaceState()){
            FindSettings.getInstance().addStringToReplace(model.getStringToReplace());
          }
          if (model.isMultipleFiles() && !model.isProjectScope()){
            FindSettings.getInstance().addDirectory(model.getDirectoryName());

            if (model.getDirectoryName()!=null) {
              myFindInProjectModel.setWithSubdirectories(model.isWithSubdirectories());
            }
          }
          okHandler.run();
        }
      });
      myFindDialog.setModal(false);
    }
    myFindDialog.show();
  }

  @NotNull
  public FindModel getFindInFileModel() {
    return myFindInFileModel;
  }

  @NotNull
  public FindModel getFindInProjectModel() {
    myFindInProjectModel.setFromCursor(false);
    myFindInProjectModel.setForward(true);
    myFindInProjectModel.setGlobal(true);
    return myFindInProjectModel;
  }

  public boolean findWasPerformed() {
    return isFindWasPerformed;
  }

  public void setFindWasPerformed() {
    isFindWasPerformed = true;
    myFindUsagesManager.clearFindingNextUsageInFile();
  }

  public FindModel getFindNextModel() {
    return myFindNextModel;
  }

  public FindModel getFindNextModel(@NotNull final Editor editor) {
    if (myFindNextModel == null) return null;

    final JComponent header = editor.getHeaderComponent();
    if (header instanceof EditorSearchComponent) {
      final EditorSearchComponent searchComponent = (EditorSearchComponent)header;
      final String textInField = searchComponent.getTextInField();
      if (!Comparing.equal(textInField, myFindInFileModel.getStringToFind()) && textInField.length() > 0) {
        FindModel patched = new FindModel();
        patched.copyFrom(myFindNextModel);
        patched.setStringToFind(textInField);
        return patched;
      }
    }

    return myFindNextModel;
  }

  public void setFindNextModel(FindModel findNextModel) {
    myFindNextModel = findNextModel;
    myBus.syncPublisher(FIND_MODEL_TOPIC).findNextModelChanged();
  }

  @NotNull
  public FindResult findString(@NotNull CharSequence text, int offset, @NotNull FindModel model){
    return findString(text, offset, model, null);
  }

  @NotNull
  @Override
  public FindResult findString(@NotNull CharSequence text, int offset, @NotNull FindModel model, @Nullable VirtualFile file) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("offset="+offset);
      LOG.debug("textlength="+text.length());
      LOG.debug(model.toString());
    }

    while(true){
      FindResult result = doFindString(text, offset, model, file);

      if (!model.isWholeWordsOnly()) {
        return result;
      }
      if (!result.isStringFound()){
        return result;
      }
      if (isWholeWord(text, result.getStartOffset(), result.getEndOffset())){
        return result;
      }

      offset = model.isForward() ? result.getStartOffset() + 1 : result.getEndOffset() - 1;
    }
  }

  private static boolean isWholeWord(CharSequence text, int startOffset, int endOffset) {
    boolean isWordStart = startOffset == 0 ||
                          !Character.isJavaIdentifierPart(text.charAt(startOffset - 1)) ||
                          startOffset > 1 && text.charAt(startOffset - 2) == '\\';

    boolean isWordEnd = endOffset == text.length() ||
                        !Character.isJavaIdentifierPart(text.charAt(endOffset)) ||
                        endOffset > 0 && !Character.isJavaIdentifierPart(text.charAt(endOffset - 1));

    return isWordStart && isWordEnd;
  }

  private static FindResult doFindString(CharSequence text, int offset, final FindModel model, @Nullable VirtualFile file) {
    String toFind = model.getStringToFind();
    if (toFind.length() == 0){
      return NOT_FOUND_RESULT;
    }

    if (model.isInCommentsOnly() || model.isInStringLiteralsOnly()) {
      return findInCommentsAndLiterals(text, offset, model, file);
    }

    if (model.isRegularExpressions()){
      return findStringByRegularExpression(text, offset, model);
    }

    final StringSearcher searcher = createStringSearcher(model);

    int index;
    if (model.isForward()){
      final int res = searcher.scan(text.subSequence(offset, text.length()));
      index = res < 0 ? -1 : res + offset;
    }
    else{
      index = searcher.scan(text.subSequence(0, offset));
    }
    if (index < 0){
      return NOT_FOUND_RESULT;
    }
    return new FindResultImpl(index, index + toFind.length());
  }

  private static StringSearcher createStringSearcher(FindModel model) {
    return new StringSearcher(model.getStringToFind(), model.isCaseSensitive(), model.isForward());
  }

  static class CommentsLiteralsSearchData {
    final VirtualFile lastFile;
    int startOffset = 0;
    final Lexer lexer;

    TokenSet tokensOfInterest;
    final StringSearcher searcher;
    final Matcher matcher;
    final Set<Language> relevantLanguages;

    public CommentsLiteralsSearchData(VirtualFile lastFile, Set<Language> relevantLanguages, Lexer lexer, TokenSet tokensOfInterest,
                                      StringSearcher searcher, Matcher matcher) {
      this.lastFile = lastFile;
      this.lexer = lexer;
      this.tokensOfInterest = tokensOfInterest;
      this.searcher = searcher;
      this.matcher = matcher;
      this.relevantLanguages = relevantLanguages;
    }
  }

  private static final Key<CommentsLiteralsSearchData> ourCommentsLiteralsSearchDataKey = Key.create("comments.literals.search.data");

  private static FindResult findInCommentsAndLiterals(CharSequence text, int offset, FindModel model, final VirtualFile file) {
    if (file == null) return NOT_FOUND_RESULT;

    FileType ftype = file.getFileType();
    Language lang = null;
    if (ftype instanceof LanguageFileType) {
      lang = ((LanguageFileType)ftype).getLanguage();
    }

    if(lang == null) return NOT_FOUND_RESULT;

    CommentsLiteralsSearchData data = model.getUserData(ourCommentsLiteralsSearchDataKey);
    if (data == null || data.lastFile != file) {
      Lexer lexer = getLexer(file, lang);

      TokenSet tokensOfInterest = TokenSet.EMPTY;
      final Language finalLang = lang;
      Set<Language> relevantLanguages = ApplicationManager.getApplication().runReadAction(new Computable<Set<Language>>() {
        public Set<Language> compute() {
          THashSet<Language> result = new THashSet<Language>();

          for(Project project:ProjectManager.getInstance().getOpenProjects()) {
            FileViewProvider viewProvider = PsiManager.getInstance(project).findViewProvider(file);
            if (viewProvider != null) {
              result.addAll(viewProvider.getLanguages());
              break;
            }
          }

          if (result.isEmpty()) {
            result.add(finalLang);
          }
          return result;
        }
      });

      for (Language relevantLanguage:relevantLanguages) {
        tokensOfInterest = addTokenTypesForLanguage(model, relevantLanguage, tokensOfInterest);
      }

      if(model.isInStringLiteralsOnly()) {
        // TODO: xml does not have string literals defined so we add XmlAttributeValue element type as convenience
        final Lexer xmlLexer = getLexer(null, Language.findLanguageByID("XML"));
        final String marker = "xxx";
        xmlLexer.start("<a href=\"" + marker+ "\" />");

        while (!marker.equals(xmlLexer.getTokenText())) {
          xmlLexer.advance();
          if (xmlLexer.getTokenType() == null) break;
        }

        IElementType convenienceXmlAttrType = xmlLexer.getTokenType();
        if (convenienceXmlAttrType != null) {
          tokensOfInterest = TokenSet.orSet(tokensOfInterest, TokenSet.create(convenienceXmlAttrType));
        }
      }

      Matcher matcher = model.isRegularExpressions() ? compileRegExp(model, ""):null;
      StringSearcher searcher = matcher != null ? null: createStringSearcher(model);
      data = new CommentsLiteralsSearchData(file, relevantLanguages, lexer, tokensOfInterest, searcher, matcher);
      model.putUserData(ourCommentsLiteralsSearchDataKey, data);
    }

    data.lexer.start(text, data.startOffset, text.length(), 0);

    IElementType tokenType;
    final Lexer lexer = data.lexer;
    TokenSet tokens = data.tokensOfInterest;

    int lastGoodOffset = 0;
    boolean scanningForward = model.isForward();
    FindResultImpl prevFindResult = NOT_FOUND_RESULT;

    while((tokenType = lexer.getTokenType()) != null) {
      if (lexer.getState() == 0) lastGoodOffset = lexer.getTokenStart();

      if (tokens.contains(tokenType)) {
        int start = lexer.getTokenStart();

        if (start >= offset || !scanningForward) {
          FindResultImpl findResult = null;

          if (data.searcher != null) {
            int i = data.searcher.scan(text, start, lexer.getTokenEnd());
            if (i != -1) findResult = new FindResultImpl(i, i + model.getStringToFind().length());
          } else {
            data.matcher.reset(text.subSequence(start, lexer.getTokenEnd()));
            if (data.matcher.find()) {
              int matchStart = data.matcher.start();
              findResult = new FindResultImpl(start + matchStart, start + data.matcher.end());
            }
          }

          if (findResult != null) {
            if (scanningForward) {
              data.startOffset = lastGoodOffset;
              return findResult;
            } else {
              if (start >= offset) return prevFindResult;
              prevFindResult = findResult;
            }
          }
        }
      } else {
        Language tokenLang = tokenType.getLanguage();
        if (tokenLang != lang && tokenLang != Language.ANY && !data.relevantLanguages.contains(tokenLang)) {
          tokens = addTokenTypesForLanguage(model, tokenLang, tokens);
          data.tokensOfInterest = tokens;
          data.relevantLanguages.add(tokenLang);
        }
      }

      lexer.advance();
    }

    return prevFindResult;
  }

  private static TokenSet addTokenTypesForLanguage(FindModel model, Language lang, TokenSet tokensOfInterest) {
    ParserDefinition definition = LanguageParserDefinitions.INSTANCE.forLanguage(lang);
    if (definition != null) {
      tokensOfInterest = TokenSet.orSet(tokensOfInterest, model.isInCommentsOnly() ? definition.getCommentTokens(): TokenSet.EMPTY);
      tokensOfInterest = TokenSet.orSet(tokensOfInterest, model.isInStringLiteralsOnly() ? definition.getStringLiteralElements() : TokenSet.EMPTY);
    }
    return tokensOfInterest;
  }

  private static Lexer getLexer(VirtualFile file, Language lang) {
    SyntaxHighlighter syntaxHighlighter = SyntaxHighlighterFactory.getSyntaxHighlighter(lang, null, file);
    assert syntaxHighlighter != null:"Syntax highlighter is null:"+file;
    return syntaxHighlighter.getHighlightingLexer();
  }

  private static FindResult findStringByRegularExpression(CharSequence text, int startOffset, FindModel model) {
    Matcher matcher = compileRegExp(model, text);

    if (model.isForward()){
      if (matcher.find(startOffset)) {
        if (matcher.end() <= text.length()) {
          return new FindResultImpl(matcher.start(), matcher.end());
        }
      }
      return NOT_FOUND_RESULT;
    }
    else {
      int start = -1;
      int end = -1;
      while(matcher.find() && matcher.end() < startOffset){
        start = matcher.start();
        end = matcher.end();
      }
      if (start < 0){
        return NOT_FOUND_RESULT;
      }
      return new FindResultImpl(start, end);
    }
  }

  private static Matcher compileRegExp(FindModel model, CharSequence text) {
    String toFind = model.getStringToFind();

    Pattern pattern;
    try {
      pattern = Pattern.compile(toFind, model.isCaseSensitive() ? Pattern.MULTILINE : Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);
    }
    catch(PatternSyntaxException e){
      LOG.error(e);
      return null;
    }

    return pattern.matcher(text);
  }

  public String getStringToReplace(@NotNull String foundString, @NotNull FindModel model) {
    String toReplace = model.getStringToReplace();
    if (model.isRegularExpressions()) {
      return getStringToReplaceByRegexp0(foundString, model);
    }
    if (model.isPreserveCase()) {
      return replaceWithCaseRespect (toReplace, foundString);
    }
    return toReplace;
  }

  @Override
  public String getStringToReplace(@NotNull String foundString, @NotNull FindModel model, int startOffset, @NotNull String documentText) {
    String toReplace = model.getStringToReplace();
    if (model.isRegularExpressions()) {
      return getStringToReplaceByRegexp(foundString, model, documentText, startOffset);
    }
    if (model.isPreserveCase()) {
      return replaceWithCaseRespect (toReplace, foundString);
    }
    return toReplace;
  }

  private String getStringToReplaceByRegexp(@NotNull String foundString, @NotNull final FindModel model, @NotNull String text, int startOffset) {
    Matcher matcher = compileRegExp(model, text);

    if (model.isForward()){
      if (!matcher.find(startOffset)) {
        return null;
      }
      if (matcher.end() > text.length()) {
        return null;
      }
    }
    else {
      int start = -1;
      while(matcher.find() && matcher.end() < startOffset){
        start = matcher.start();
      }
      if (start < 0){
        return null;
      }
    }
    try {
      StringBuffer replaced = new StringBuffer();
      matcher.appendReplacement(replaced, model.getStringToReplace());

      return replaced.substring(matcher.start());
    }
    catch (Exception e) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          Messages.showErrorDialog(myProject, FindBundle.message("find.replace.invalid.replacement.string", model.getStringToReplace()),
                                   FindBundle.message("find.replace.invalid.replacement.string.title"));
        }
      });
      return null;
    }
  }

  private String getStringToReplaceByRegexp0(String foundString, final FindModel model) {
    String toFind = model.getStringToFind();
    String toReplace = model.getStringToReplace();
    Pattern pattern;
    try{
      int flags = Pattern.MULTILINE;
      if (!model.isCaseSensitive()) {
        flags |= Pattern.CASE_INSENSITIVE;
      }
      pattern = Pattern.compile(toFind, flags);
    }
    catch(PatternSyntaxException e){
      return toReplace;
    }

    Matcher matcher = pattern.matcher(foundString);
    if (matcher.matches()) {
      try {
        return matcher.replaceAll(StringUtil.unescapeStringCharacters(toReplace));
      }
      catch (Exception e) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            Messages.showErrorDialog(myProject, FindBundle.message("find.replace.invalid.replacement.string", model.getStringToReplace()),
                                     FindBundle.message("find.replace.invalid.replacement.string.title"));
          }
        });
        return null;
      }
    }
    else {
      // There are valid situations (for example, IDEADEV-2543 or positive lookbehind assertions)
      // where an expression which matches a string in context will not match the same string
      // separately).
      return toReplace;
    }
  }

  private static String replaceWithCaseRespect(String toReplace, String foundString) {
    if (foundString.length() == 0 || toReplace.length() == 0) return toReplace;
    StringBuilder buffer = new StringBuilder();

    if (Character.isUpperCase(foundString.charAt(0))) {
      buffer.append(Character.toUpperCase(toReplace.charAt(0)));
    }
    else {
      buffer.append(Character.toLowerCase(toReplace.charAt(0)));
    }
    if (toReplace.length() == 1) return buffer.toString();

    if (foundString.length() == 1) {
      buffer.append(toReplace.substring(1));
      return buffer.toString();
    }

    boolean isTailUpper = true;
    boolean isTailLower = true;
    for (int i = 1; i < foundString.length(); i++) {
      isTailUpper &= Character.isUpperCase(foundString.charAt(i));
      isTailLower &= Character.isLowerCase(foundString.charAt(i));
      if (!isTailUpper && !isTailLower) break;
    }

    if (isTailUpper) {
      buffer.append(toReplace.substring(1).toUpperCase());
    }
    else if (isTailLower) {
      buffer.append(toReplace.substring(1).toLowerCase());
    }
    else {
      buffer.append(toReplace.substring(1));
    }
    return buffer.toString();
  }

  public boolean canFindUsages(@NotNull PsiElement element) {
    return element.isValid() && myFindUsagesManager.canFindUsages(element);
  }

  public void findUsages(@NotNull PsiElement element) {
    myFindUsagesManager.findUsages(element, null, null);
  }

  public void findUsagesInEditor(@NotNull PsiElement element, @NotNull FileEditor fileEditor) {
    if (fileEditor instanceof TextEditor) {
      TextEditor textEditor = (TextEditor)fileEditor;
      Editor editor = textEditor.getEditor();
      Document document = editor.getDocument();
      PsiFile psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(document);

      myFindUsagesManager.findUsages(element, psiFile, fileEditor);
    }
  }

  public boolean findNextUsageInEditor(@NotNull FileEditor fileEditor) {
    if (fileEditor instanceof TextEditor) {
      TextEditor textEditor = (TextEditor)fileEditor;
      Editor editor = textEditor.getEditor();

      FindModel model = getFindNextModel(editor);
      if (model != null && model.searchHighlighters()) {
        RangeHighlighter[] highlighters = ((HighlightManagerImpl)HighlightManager.getInstance(myProject)).getHighlighters(editor);
        if (highlighters.length > 0) {
          return highlightNextHighlighter(highlighters, editor, editor.getCaretModel().getOffset(), true, false);
        }
      }
    }

    return myFindUsagesManager.findNextUsageInFile(fileEditor);
  }

  private static boolean highlightNextHighlighter(RangeHighlighter[] highlighters, Editor editor, int offset, boolean isForward, boolean secondPass) {
    RangeHighlighter highlighterToSelect = null;
    Object wasNotFound = editor.getUserData(HIGHLIGHTER_WAS_NOT_FOUND_KEY);
    for (RangeHighlighter highlighter : highlighters) {
      int start = highlighter.getStartOffset();
      int end = highlighter.getEndOffset();
      if (highlighter.isValid() && start < end) {
        if (isForward && (start > offset || start == offset && secondPass)) {
          if (highlighterToSelect == null || highlighterToSelect.getStartOffset() > start) highlighterToSelect = highlighter;
        }
        if (!isForward && (end < offset || end == offset && secondPass)) {
          if (highlighterToSelect == null || highlighterToSelect.getEndOffset() < end) highlighterToSelect = highlighter;
        }
      }
    }
    if (highlighterToSelect != null) {
      editor.getSelectionModel().setSelection(highlighterToSelect.getStartOffset(), highlighterToSelect.getEndOffset());
      editor.getCaretModel().moveToOffset(highlighterToSelect.getStartOffset());
      ScrollType scrollType;
      if (!secondPass) {
        scrollType = isForward ? ScrollType.CENTER_DOWN : ScrollType.CENTER_UP;
      }
      else {
        scrollType = isForward ? ScrollType.CENTER_UP : ScrollType.CENTER_DOWN;
      }
      editor.getScrollingModel().scrollToCaret(scrollType);
      editor.putUserData(HIGHLIGHTER_WAS_NOT_FOUND_KEY, null);
      return true;
    }

    if (wasNotFound == null) {
      editor.putUserData(HIGHLIGHTER_WAS_NOT_FOUND_KEY, Boolean.TRUE);
      String message = FindBundle.message("find.highlight.no.more.highlights.found");
      if (isForward) {
        AnAction action=ActionManager.getInstance().getAction(IdeActions.ACTION_FIND_NEXT);
        String shortcutsText=KeymapUtil.getFirstKeyboardShortcutText(action);
        if (shortcutsText.length() > 0) {
          message = FindBundle.message("find.search.again.from.top.hotkey.message", message, shortcutsText);
        }
        else {
          message = FindBundle.message("find.search.again.from.top.action.message", message);
        }
      }
      else {
        AnAction action=ActionManager.getInstance().getAction(IdeActions.ACTION_FIND_PREVIOUS);
        String shortcutsText=KeymapUtil.getFirstKeyboardShortcutText(action);
        if (shortcutsText.length() > 0) {
          message = FindBundle.message("find.search.again.from.bottom.hotkey.message", message, shortcutsText);
        }
        else {
          message = FindBundle.message("find.search.again.from.bottom.action.message", message);
        }
      }
      JComponent component = HintUtil.createInformationLabel(message);
      final LightweightHint hint = new LightweightHint(component);
      HintManagerImpl.getInstanceImpl().showEditorHint(hint, editor, HintManager.UNDER, HintManager.HIDE_BY_ANY_KEY |
                                                                                        HintManager.HIDE_BY_TEXT_CHANGE | HintManager.HIDE_BY_SCROLLING, 0, false);
      return true;
    } else if (!secondPass) {
      offset = isForward ? 0 : editor.getDocument().getTextLength();
      return highlightNextHighlighter(highlighters, editor, offset, isForward, true);
    }

    return false;
  }

  public boolean findPreviousUsageInEditor(@NotNull FileEditor fileEditor) {
    if (fileEditor instanceof TextEditor) {
      TextEditor textEditor = (TextEditor)fileEditor;
      Editor editor = textEditor.getEditor();

      FindModel model = getFindNextModel(editor);
      if (model != null && model.searchHighlighters()) {
        RangeHighlighter[] highlighters = ((HighlightManagerImpl)HighlightManager.getInstance(myProject)).getHighlighters(editor);
        if (highlighters.length > 0) {
          return highlightNextHighlighter(highlighters, editor, editor.getCaretModel().getOffset(), false, false);
        }
      }
    }

    return myFindUsagesManager.findPreviousUsageInFile(fileEditor);
  }

  public FindUsagesManager getFindUsagesManager() {
    return myFindUsagesManager;
  }
}

