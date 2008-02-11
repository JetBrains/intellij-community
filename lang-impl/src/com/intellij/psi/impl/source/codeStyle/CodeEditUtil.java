package com.intellij.psi.impl.source.codeStyle;

import com.intellij.formatting.FormatterEx;
import com.intellij.formatting.FormattingModel;
import com.intellij.formatting.FormattingModelBuilder;
import com.intellij.formatting.IndentInfo;
import com.intellij.lang.*;
import com.intellij.openapi.command.AbnormalCommandTerminationException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.TokenType;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.impl.source.jsp.jspJava.OuterLanguageElement;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CodeEditUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.codeStyle.CodeEditUtil");
  private static final Key<Boolean> GENERATED_FLAG = new Key<Boolean>("CREATED BY IDEA");
  private static final Key<Integer> INDENT_INFO = new Key<Integer>("INDENTATION");
  private static final Key<Boolean> REFORMAT_KEY = new Key<Boolean>("REFORMAT BEFORE THIS ELEMENT");

  public static final Key<Boolean> OUTER_OK = new Key<Boolean>("OUTER_OK");

  private CodeEditUtil() {
  }

  public static void addChild(CompositeElement parent, ASTNode child, ASTNode anchorBefore) {
    addChildren(parent, child, child, anchorBefore);
  }

  public static void removeChild(CompositeElement parent, @NotNull ASTNode child) {
    removeChildren(parent, child, child);
  }

  public static ASTNode addChildren(CompositeElement parent, ASTNode first, ASTNode last, ASTNode anchorBefore) {
    LOG.assertTrue(first != null);
    LOG.assertTrue(last != null);
    ASTNode lastChild = last.getTreeNext();
    ASTNode current = first;
    while(current != lastChild){
      saveWhitespacesInfo(current);
      checkForOuters(current);
      current = current.getTreeNext();
    }

    if (anchorBefore != null && isComment(anchorBefore.getElementType())) {
      final ASTNode anchorPrev = anchorBefore.getTreePrev();
      if (anchorPrev != null && anchorPrev.getElementType() == TokenType.WHITE_SPACE) {
        anchorBefore = anchorPrev;
        /*
        final int blCount = getBlankLines(anchorPrev.getText());
        if (bl)
        */
      }
    }

    parent.addChildren(first, lastChild, anchorBefore);
    final ASTNode firstAddedLeaf = findFirstLeaf(first, last);
    final ASTNode prevLeaf = TreeUtil.prevLeaf(first);
    if(firstAddedLeaf != null){
      ASTNode placeHolderEnd = makePlaceHolderBetweenTokens(prevLeaf, firstAddedLeaf, isFormattingRequiered(prevLeaf, first), false);
      if(placeHolderEnd != prevLeaf && first == firstAddedLeaf) first = placeHolderEnd;
      final ASTNode lastAddedLeaf = findLastLeaf(first, last);
      placeHolderEnd = makePlaceHolderBetweenTokens(lastAddedLeaf, TreeUtil.nextLeaf(last), true, false);
      if(placeHolderEnd != lastAddedLeaf && lastAddedLeaf == first) first = placeHolderEnd;
    }
    else makePlaceHolderBetweenTokens(prevLeaf, TreeUtil.nextLeaf(last), isFormattingRequiered(prevLeaf, first), false);
    return first;
  }

  private static boolean isComment(IElementType type) {
    final ParserDefinition def = LanguageParserDefinitions.INSTANCE.forLanguage(type.getLanguage());
    return def != null && def.getCommentTokens().contains(type);
  }

  private static boolean isFormattingRequiered(final ASTNode prevLeaf, ASTNode first) {
    while(first != null) {
      ASTNode current = prevLeaf;
      while (current != null) {
        if (current.getTreeNext() == first) return true;
        current = current.getTreeParent();
      }
      final ASTNode parent = first.getTreeParent();
      if (parent != null && parent.getTextRange().equals(first.getTextRange())) {
        first = parent;
      }
      else {
        break;
      }
    }
    return false;
  }

  public static void checkForOuters(final ASTNode element) {
    if (element instanceof OuterLanguageElement && element.getCopyableUserData(OUTER_OK) == null) throw new AbnormalCommandTerminationException();
    if (element instanceof CompositeElement) {
      TreeElement child = ((CompositeElement)element).firstChild;
      while (child != null) {
        checkForOuters(child);
        child = child.next;
      }
    }
  }

  public static void saveWhitespacesInfo(final ASTNode first) {
    if(first == null || isNodeGenerated(first) || getOldIndentation(first) >= 0) return;
    final PsiFile containingFile = first.getPsi().getContainingFile();
    final Helper helper = new Helper(containingFile.getFileType(), containingFile.getProject());
    setOldIndentation((TreeElement)first, helper.getIndent(first));
  }

  public static int getOldIndentation(ASTNode node){
    if(node == null) return -1;
    final Integer stored = node.getCopyableUserData(INDENT_INFO);
    return stored != null ? stored : -1;
  }

  public static void removeChildren(CompositeElement parent, @NotNull ASTNode first, @NotNull ASTNode last) {
    final boolean tailingElement = last.getStartOffset() + last.getTextLength() == parent.getStartOffset() + parent.getTextLength();
    final boolean forceReformat = needToForceReformat(parent, first, last);
    saveWhitespacesInfo(first);

    TreeElement child = (TreeElement)first;
    while (child != null) {
      //checkForOuters(child);
      if (child == last) break;
      child = child.next;
    }

    final ASTNode prevLeaf = TreeUtil.prevLeaf(first);
    final ASTNode nextLeaf = TreeUtil.nextLeaf(first);
    parent.removeRange(first, last.getTreeNext());
    ASTNode nextLeafToAdjust = nextLeaf;
    if (nextLeafToAdjust != null && nextLeafToAdjust.getTreeParent() == null) {
      //next element has invalidated
      nextLeafToAdjust = prevLeaf.getTreeNext();
    }
    makePlaceHolderBetweenTokens(prevLeaf, nextLeafToAdjust, forceReformat, tailingElement);
  }

  private static boolean needToForceReformat(final CompositeElement parent, final ASTNode first, final ASTNode last) {
    return parent == null || first.getStartOffset() != parent.getStartOffset() ||
           parent.getText().trim().length() == getTrimmedTextLength(first, last) && needToForceReformat(parent.getTreeParent(), parent, parent);
  }

  private static int getTrimmedTextLength(ASTNode first, final ASTNode last) {
    final StringBuilder buffer = new StringBuilder();
    while(first != last.getTreeNext()) {
      buffer.append(first.getText());
      first = first.getTreeNext();
    }
    return buffer.toString().trim().length();
  }

  public static void replaceChild(CompositeElement parent, @NotNull ASTNode oldChild, @NotNull ASTNode newChild) {
    saveWhitespacesInfo(oldChild);
    saveWhitespacesInfo(newChild);
    checkForOuters(oldChild);
    checkForOuters(newChild);
    parent.replaceChild(oldChild, newChild);
    final LeafElement firstLeaf = TreeUtil.findFirstLeaf(newChild);
    final ASTNode prevToken = TreeUtil.prevLeaf(newChild);
    if (firstLeaf != null) {
      final ASTNode nextLeaf = TreeUtil.nextLeaf(newChild);
      makePlaceHolderBetweenTokens(prevToken, firstLeaf, isFormattingRequiered(prevToken, newChild), false);
      if (nextLeaf != null && !CharArrayUtil.containLineBreaks(nextLeaf.getText())) {
        makePlaceHolderBetweenTokens(TreeUtil.prevLeaf(nextLeaf), nextLeaf, false, false);
      }
    }
    else {
      makePlaceHolderBetweenTokens(prevToken, TreeUtil.nextLeaf(newChild), isFormattingRequiered(prevToken, newChild), false);
    }
  }

  @Nullable
  private static ASTNode findFirstLeaf(ASTNode first, ASTNode last) {
    do{
      final LeafElement leaf = TreeUtil.findFirstLeaf(first);
      if(leaf != null) return leaf;
      first = first.getTreeNext();
      if (first == null) return null;
    }
    while(first != last);
    return null;
  }

  @Nullable
  private static ASTNode findLastLeaf(ASTNode first, ASTNode last) {
    do{
      final LeafElement leaf = TreeUtil.findLastLeaf(last);
      if(leaf != null) return leaf;
      last = last.getTreePrev();
      if (last == null) return null;
    }
    while(first != last);
    return null;
  }

  private static ASTNode makePlaceHolderBetweenTokens(ASTNode left, final ASTNode right, boolean forceReformat, final boolean normalizeTailingWhitespace) {
    if(right == null) return left;

    markToReformatBefore(right, false);
    if(left == null){
      markToReformatBefore(right, true);
    }
    else if(left.getElementType() == TokenType.WHITE_SPACE && left.getTreeNext() == null && normalizeTailingWhitespace){
      // handle tailing whitespaces if element on the left has been removed
      left.getTreeParent().removeChild(left);
      markToReformatBeforeOrInsertWhitespace(left, right, right.getTreeParent().getPsi().getManager());
      left = right;
    }
    else if(left.getElementType() == TokenType.WHITE_SPACE && right.getElementType() == TokenType.WHITE_SPACE) {
      final String text;
      final int leftBlankLines = getBlankLines(left.getText());
      final int rightBlankLines = getBlankLines(right.getText());
      final boolean leaveRightText = leftBlankLines < rightBlankLines;
      if (leftBlankLines == 0 && rightBlankLines == 0) text = left.getText() + right.getText();
      else if (leaveRightText) text = right.getText();
      else text = left.getText();
      if(leaveRightText || forceReformat){
        final LeafElement merged =
          Factory.createSingleLeafElement(TokenType.WHITE_SPACE, text, 0, text.length(), null, left.getPsi().getManager());
        if(!leaveRightText){
          left.getTreeParent().replaceChild(left, merged);
          right.getTreeParent().removeChild(right);
        }
        else {
          right.getTreeParent().replaceChild(right, merged);
          left.getTreeParent().removeChild(left);
        }
        left = merged;
      }
      else right.getTreeParent().removeChild(right);
    }
    else if(left.getElementType() != TokenType.WHITE_SPACE || forceReformat){
      if(right.getElementType() == TokenType.WHITE_SPACE){
        markWhitespaceForReformat(right);
      }
      else if(left.getElementType() == TokenType.WHITE_SPACE){
        markWhitespaceForReformat(left);
      }
      else markToReformatBeforeOrInsertWhitespace(left, right, right.getTreeParent().getPsi().getManager());
    }
    return left;
  }

  private static void markWhitespaceForReformat(final ASTNode right) {
    final String text = right.getText();
    final LeafElement merged = Factory.createSingleLeafElement(TokenType.WHITE_SPACE, text, 0, text.length(), null,
                                                               right.getPsi().getManager());
    right.getTreeParent().replaceChild(right, merged);
  }

  private static void markToReformatBeforeOrInsertWhitespace(final ASTNode left, @NotNull final ASTNode right, PsiManager manager) {
    final Language leftLang = left != null ? left.getElementType().getLanguage() : null;
    final Language rightLang = right.getElementType().getLanguage();
    final ParserDefinition parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(rightLang);
    LeafElement generatedWhitespace = null;
    if(leftLang == rightLang && parserDefinition != null){
      //noinspection EnumSwitchStatementWhichMissesCases
      switch(parserDefinition.spaceExistanceTypeBetweenTokens(left, right)){
        case MUST:
          generatedWhitespace = Factory.createSingleLeafElement(TokenType.WHITE_SPACE, " ", 0, 1, null, manager);
          break;
        case MUST_LINE_BREAK:
          generatedWhitespace = Factory.createSingleLeafElement(TokenType.WHITE_SPACE, "\n", 0, 1, null, manager);
          break;
        default:
          generatedWhitespace = null;
      }
    }
    if(generatedWhitespace != null){
      final TreeUtil.CommonParentState parentState = new TreeUtil.CommonParentState();
      TreeUtil.prevLeaf((TreeElement)right, parentState);
      parentState.nextLeafBranchStart.getTreeParent().addChild(generatedWhitespace, parentState.nextLeafBranchStart);
    }
    else markToReformatBefore(right, true);
  }

  public static void markToReformatBefore(final ASTNode right, boolean value) {
    if (value) {
      right.putCopyableUserData(REFORMAT_KEY, true);
    }
    else {
      right.putCopyableUserData(REFORMAT_KEY, null);
    }
  }

  private static int getBlankLines(final String text) {
    int result = 0;
    int currentIndex = -1;
    while((currentIndex = text.indexOf('\n', currentIndex + 1)) >= 0) result++;
    return result;
  }

  public static String getStringWhiteSpaceBetweenTokens(ASTNode first, ASTNode second, PsiFile file) {
    final FormattingModelBuilder modelBuilder = LanguageFormatting.INSTANCE.forContext(file);
    if (modelBuilder == null) {
      final LeafElement leafElement = TreeUtil.nextLeaf((TreeElement)first, null);
      if (leafElement != second) {
        return leafElement.getText();
      }
      else {
        return null;
      }
    }
    else {
      final CodeStyleSettings settings = CodeStyleSettingsManager.getInstance(file.getProject()).getCurrentSettings();
      return getWhiteSpaceBeforeToken(second, file, true).generateNewWhiteSpace(settings.getIndentOptions(file.getFileType()));
    }

  }

  private static IndentInfo getWhiteSpaceBeforeToken(final ASTNode tokenNode, final PsiFile file, final boolean mayChangeLineFeeds) {
    LOG.assertTrue(tokenNode != null);

    final Project project = file.getProject();
    final CodeStyleSettings settings = CodeStyleSettingsManager.getInstance(project).getCurrentSettings();
    final int tokenStartOffset = tokenNode.getStartOffset();

    final boolean oldValue = settings.XML_KEEP_LINE_BREAKS;
    final int oldKeepBlankLines = settings.XML_KEEP_BLANK_LINES;
    settings.XML_KEEP_BLANK_LINES = 0;
    try {
      final FormattingModelBuilder builder = LanguageFormatting.INSTANCE.forContext(file);
      final PsiElement element = file.findElementAt(tokenStartOffset);

      if (builder != null && LanguageFormatting.INSTANCE.forContext(element) != null) {
        final TextRange textRange = element.getTextRange();
        final FormattingModel model = builder.createModel(file, settings);
        return FormatterEx.getInstanceEx().getWhiteSpaceBefore(model.getDocumentModel(), model.getRootBlock(), settings,
                                                               settings.getIndentOptions(file.getFileType()), textRange,
                                                               mayChangeLineFeeds);
      }
      else {
        return new IndentInfo(0, 0, 0);
      }

    }
    finally {
      settings.XML_KEEP_LINE_BREAKS = oldValue;
      settings.XML_KEEP_BLANK_LINES = oldKeepBlankLines;
    }
  }

  public static boolean isNodeGenerated(final ASTNode node) {
    return node == null || node.getCopyableUserData(GENERATED_FLAG) != null;
  }

  public static void setNodeGenerated(final ASTNode next, final boolean value) {
    if(next == null) return;
    if(value) next.putCopyableUserData(GENERATED_FLAG, true);
    else next.putCopyableUserData(GENERATED_FLAG, null);
  }

  public static void setOldIndentation(final TreeElement treeElement, final int oldIndentation) {
    if(treeElement == null) return;
    if(oldIndentation >= 0) treeElement.putCopyableUserData(INDENT_INFO, oldIndentation);
    else treeElement.putCopyableUserData(INDENT_INFO, null);
  }

  public static boolean isMarkedToReformatBefore(final TreeElement element) {
    return element.getCopyableUserData(REFORMAT_KEY) != null;
  }
}
