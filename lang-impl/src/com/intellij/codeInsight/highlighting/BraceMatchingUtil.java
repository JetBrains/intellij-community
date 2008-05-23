package com.intellij.codeInsight.highlighting;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageBraceMatching;
import com.intellij.lang.PairedBraceMatcher;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeExtensionPoint;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Stack;

public class BraceMatchingUtil {
  public static final int UNDEFINED_TOKEN_GROUP = -1;

  private BraceMatchingUtil() {}

  public static boolean isPairedBracesAllowedBeforeTypeInFileType(final IElementType lbraceType, final IElementType tokenType, final FileType fileType) {
    try {
      return getBraceMatcher(fileType).isPairedBracesAllowedBeforeType(lbraceType, tokenType);
    } catch (AbstractMethodError incompatiblePluginThatWeDoNotCare) {}
    return true;
  }

  private static final HashMap<FileType,BraceMatcher> BRACE_MATCHERS = new HashMap<FileType, BraceMatcher>();

  public static void registerBraceMatcher(FileType fileType,BraceMatcher braceMatcher) {
    BRACE_MATCHERS.put(fileType, braceMatcher);
  }

  private static final Stack<IElementType> ourBraceStack = new Stack<IElementType>();
  private static final Stack<String> ourTagNameStack = new Stack<String>();

  private static class MatchBraceContext {
    CharSequence fileText;
    FileType fileType;
    HighlighterIterator iterator;
    boolean forward;

    IElementType brace1Token;
    int group;
    String brace1TagName;
    boolean isStrict;
    boolean isCaseSensitive;
    private BraceMatcher myMatcher;

    MatchBraceContext(CharSequence _fileText, FileType _fileType, HighlighterIterator _iterator, boolean _forward) {
      fileText = _fileText;
      fileType = _fileType;
      iterator = _iterator;
      forward = _forward;

      myMatcher = getBraceMatcher(_fileType);
      brace1Token = iterator.getTokenType();
      group = getTokenGroup(brace1Token, fileType);
      brace1TagName = myMatcher == null ? null : getTagName(myMatcher,fileText, iterator);

      isStrict = myMatcher != null && isStrictTagMatching(myMatcher,fileType, group);
      isCaseSensitive = myMatcher != null && areTagsCaseSensitive(myMatcher,fileType, group);
    }

    MatchBraceContext(CharSequence _fileText, FileType _fileType, HighlighterIterator _iterator, boolean _forward,
                      boolean _strict) {
      this(_fileText, _fileType, _iterator, _forward);
      isStrict = _strict;
    }

    boolean doBraceMatch() {
      ourBraceStack.clear();
      ourTagNameStack.clear();
      ourBraceStack.push(brace1Token);
      if (isStrict){
        ourTagNameStack.push(brace1TagName);
      }
      boolean matched = false;
      while(true){
        if (!forward){
          iterator.retreat();
        }
        else{
          iterator.advance();
        }
        if (iterator.atEnd()) {
          break;
        }

        IElementType tokenType = iterator.getTokenType();

        if (getTokenGroup(tokenType, fileType) == group) {
          String tagName = myMatcher == null ? null : getTagName(myMatcher,fileText, iterator);
          if (!isStrict && !Comparing.equal(brace1TagName, tagName, isCaseSensitive)) continue;
          if (forward ? isLBraceToken(iterator, fileText, fileType) : isRBraceToken(iterator, fileText, fileType)){
            ourBraceStack.push(tokenType);
            if (isStrict){
              ourTagNameStack.push(tagName);
            }
          }
          else if (forward ? isRBraceToken(iterator, fileText,fileType) : isLBraceToken(iterator, fileText, fileType)){
            IElementType topTokenType = ourBraceStack.pop();
            String topTagName = null;
            if (isStrict){
              topTagName = ourTagNameStack.pop();
            }

            if (!isPairBraces(topTokenType, tokenType, fileType)
              || isStrict && !Comparing.equal(topTagName, tagName, isCaseSensitive)
            ){
              matched = false;
              break;
            }

            if (ourBraceStack.size() == 0){
              matched = true;
              break;
            }
          }
        }
      }
      return matched;
    }
  }

  public static synchronized boolean matchBrace(CharSequence fileText, FileType fileType, HighlighterIterator iterator, 
                                                boolean forward) {
    return new MatchBraceContext(fileText, fileType, iterator, forward).doBraceMatch();
  }


  public static synchronized boolean matchBrace(CharSequence fileText, FileType fileType, HighlighterIterator iterator,
                                                boolean forward, boolean isStrict) {
    return new MatchBraceContext(fileText, fileType, iterator, forward, isStrict).doBraceMatch();
  }

  public static boolean findStructuralLeftBrace(FileType fileType, HighlighterIterator iterator, CharSequence fileText) {
    ourBraceStack.clear();
    ourTagNameStack.clear();

    BraceMatcher matcher = getBraceMatcher(fileType);
    if (matcher == null) return false;

    while (!iterator.atEnd()) {
      if (isStructuralBraceToken(fileType, iterator,fileText)) {
        if (isRBraceToken(iterator, fileText, fileType)) {
          ourBraceStack.push(iterator.getTokenType());
          ourTagNameStack.push(getTagName(matcher,fileText, iterator));
        }
        if (isLBraceToken(iterator, fileText, fileType)) {
          if (ourBraceStack.size() == 0) return true;

          final int group = matcher.getBraceTokenGroupId(iterator.getTokenType());

          final IElementType topTokenType = ourBraceStack.pop();
          final IElementType tokenType = iterator.getTokenType();

          boolean isStrict = isStrictTagMatching(matcher,fileType, group);
          boolean isCaseSensitive = areTagsCaseSensitive(matcher,fileType, group);

          String topTagName = null;
          String tagName = null;
          if (isStrict){
            topTagName = ourTagNameStack.pop();
            tagName = getTagName(matcher,fileText, iterator);
          }

          if (!isPairBraces(topTokenType, tokenType, fileType)
            || isStrict && !Comparing.equal(topTagName, tagName, isCaseSensitive)) {
            return false;
          }
        }
      }

      iterator.retreat();
    }

    return false;
  }

  public static boolean isStructuralBraceToken(FileType fileType, HighlighterIterator iterator,CharSequence text) {
    BraceMatcher matcher = getBraceMatcher(fileType);
    return matcher != null && matcher.isStructuralBrace(iterator, text, fileType);
  }

  public static boolean isLBraceToken(HighlighterIterator iterator, CharSequence fileText, FileType fileType){
    final BraceMatcher braceMatcher = getBraceMatcher(fileType);

    return braceMatcher != null && braceMatcher.isLBraceToken(iterator, fileText, fileType);
  }

  public static boolean isRBraceToken(HighlighterIterator iterator, CharSequence fileText, FileType fileType){
    final BraceMatcher braceMatcher = getBraceMatcher(fileType);

    return braceMatcher != null && braceMatcher.isRBraceToken(iterator, fileText, fileType);
  }

  public static boolean isPairBraces(IElementType tokenType1, IElementType tokenType2, FileType fileType){
    BraceMatcher matcher = getBraceMatcher(fileType);
    return matcher != null && matcher.isPairBraces(tokenType1, tokenType2);
  }

  private static int getTokenGroup(IElementType tokenType, FileType fileType){
    BraceMatcher matcher = getBraceMatcher(fileType);
    if (matcher!=null) return matcher.getBraceTokenGroupId(tokenType);
    return UNDEFINED_TOKEN_GROUP;
  }

  // TODO: better name for this method
  public static int findLeftmostLParen(HighlighterIterator iterator, IElementType lparenTokenType, CharSequence fileText, FileType fileType){
    int lastLbraceOffset = -1;

    Stack<IElementType> braceStack = new Stack<IElementType>();
    for( ; !iterator.atEnd(); iterator.retreat()){
      final IElementType tokenType = iterator.getTokenType();

      if (isLBraceToken(iterator, fileText, fileType)){
        if (braceStack.size() > 0){
          IElementType topToken = braceStack.pop();
          if (!isPairBraces(tokenType, topToken, fileType)) {
            break; // unmatched braces
          }
        }
        else{
          if (tokenType == lparenTokenType){
            lastLbraceOffset = iterator.getStart();
          }
          else{
            break;
          }
        }
      }
      else if (isRBraceToken(iterator, fileText, fileType )){
        braceStack.push(iterator.getTokenType());
      }
    }

    return lastLbraceOffset;
  }

  // TODO: better name for this method
  public static int findRightmostRParen(HighlighterIterator iterator, IElementType rparenTokenType, CharSequence fileText, FileType fileType) {
    int lastRbraceOffset = -1;

    Stack<IElementType> braceStack = new Stack<IElementType>();
    for(; !iterator.atEnd(); iterator.advance()){
      final IElementType tokenType = iterator.getTokenType();

      if (isRBraceToken(iterator, fileText, fileType)){
        if (braceStack.size() > 0){
          IElementType topToken = braceStack.pop();
          if (!isPairBraces(tokenType, topToken, fileType)) {
            break; // unmatched braces
          }
        }
        else{
          if (tokenType == rparenTokenType){
            lastRbraceOffset = iterator.getStart();
          }
          else{
            break;
          }
        }
      }
      else if (isLBraceToken(iterator, fileText, fileType)){
        braceStack.push(iterator.getTokenType());
      }
    }

    return lastRbraceOffset;
  }

  private static class BraceMatcherHolder {
    private static final BraceMatcher ourDefaultBraceMatcher = new DefaultBraceMatcher();
  }

  public static BraceMatcher getBraceMatcher(FileType fileType) {
    BraceMatcher braceMatcher = BRACE_MATCHERS.get(fileType);
    if (braceMatcher==null) {
      for(FileTypeExtensionPoint<BraceMatcher> ext:Extensions.getExtensions(BraceMatcher.EP_NAME)) {
        if (fileType.getName().equals(ext.filetype)) {
          braceMatcher = ext.getInstance();
          break;
        }
      }
      if (braceMatcher == null) {
        if (fileType instanceof LanguageFileType) {
          final Language language = ((LanguageFileType)fileType).getLanguage();
          final PairedBraceMatcher matcher = LanguageBraceMatching.INSTANCE.forLanguage(language);
          if (matcher != null) {
            braceMatcher = new PairedBraceMatcherAdapter(matcher,language);
          }
        }
      }
      if (braceMatcher == null) {
        braceMatcher = BraceMatcherHolder.ourDefaultBraceMatcher;
      }
      BRACE_MATCHERS.put(fileType, braceMatcher);
    }
    return braceMatcher;
  }

  private static boolean isStrictTagMatching(BraceMatcher matcher,final FileType fileType, final int group) {
    if (matcher instanceof XmlAwareBraceMatcher) return ((XmlAwareBraceMatcher)matcher).isStrictTagMatching(fileType, group);
    return false;
  }

  private static boolean areTagsCaseSensitive(BraceMatcher matcher,final FileType fileType, final int tokenGroup) {
    if (matcher instanceof XmlAwareBraceMatcher) return ((XmlAwareBraceMatcher)matcher).areTagsCaseSensitive(fileType, tokenGroup);
    return false;
  }

  private static String getTagName(BraceMatcher matcher,CharSequence fileText, HighlighterIterator iterator) {
    if (matcher instanceof XmlAwareBraceMatcher) return ((XmlAwareBraceMatcher)matcher).getTagName(fileText, iterator);
    return null;
  }

  private static class DefaultBraceMatcher implements BraceMatcher {
    public int getBraceTokenGroupId(final IElementType tokenType) {
      return UNDEFINED_TOKEN_GROUP;
    }

    public boolean isLBraceToken(final HighlighterIterator iterator, final CharSequence fileText, final FileType fileType) {
      return false;
    }

    public boolean isRBraceToken(final HighlighterIterator iterator, final CharSequence fileText, final FileType fileType) {
      return false;
    }

    public boolean isPairBraces(final IElementType tokenType, final IElementType tokenType2) {
      return false;
    }

    public boolean isStructuralBrace(final HighlighterIterator iterator, final CharSequence text, final FileType fileType) {
      return false;
    }

    public IElementType getOppositeBraceTokenType(@NotNull final IElementType type) {
      return null;
    }

    public boolean isPairedBracesAllowedBeforeType(@NotNull final IElementType lbraceType, @Nullable final IElementType contextType) {
      return true;
    }

    public int getCodeConstructStart(final PsiFile file, final int openingBraceOffset) {
      return openingBraceOffset;
    }
  }
}
