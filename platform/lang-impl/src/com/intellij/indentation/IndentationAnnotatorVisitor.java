package com.intellij.indentation;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author oleg
 */
public class IndentationAnnotatorVisitor extends PsiElementVisitor {
  protected final AnnotationHolder myHolder;
  private final IElementType myIndentTokenType;
  private final IElementType myEolTokenType;
  private final TokenSet myCommentTypes;

  public IndentationAnnotatorVisitor(@NotNull final AnnotationHolder holder,
                                     final IElementType indentTokenType,
                                     final IElementType eolTokenType,
                                     final TokenSet commentTypes) {
    myHolder = holder;
    myIndentTokenType = indentTokenType;
    myEolTokenType = eolTokenType;
    myCommentTypes = commentTypes;
  }

  @SuppressWarnings({"ConstantConditions"})
  @Override
  public void visitFile(final PsiFile file) {
    int lastIndent = 0;
    IndentInfo indentInfo = null;
    PsiElement leaf = PsiTreeUtil.getDeepestFirst(file);
    if (leaf == null || leaf instanceof PsiFile){
      return;
    }
    PsiElement nextLeaf = PsiTreeUtil.nextLeaf(leaf);

    // First leaf check
    if (leaf.getNode().getElementType() == myIndentTokenType){
      myHolder.createErrorAnnotation(leaf, "Indenting at the beginning of the document is illegal");
    }
    leaf = nextLeaf;
    nextLeaf = leaf != null ? PsiTreeUtil.nextLeaf(leaf) : null;

    // Iterating over leafs
    while (leaf != null) {
      final IElementType leafType = leaf.getNode().getElementType();
      if (leafType == myIndentTokenType && nextLeaf != null) {
        final IElementType nextLeafType = nextLeaf.getNode().getElementType();
        if (nextLeafType != myEolTokenType && !myCommentTypes.contains(nextLeafType)){
          final String currentIndentText = leaf.getText();
          final IndentInfo currentIndent = getIndent(currentIndentText);
          final int currentIndentLength = currentIndent.length;
          // Check if spaces and tabs are mixed
          if (currentIndentLength == -1){
            myHolder.createErrorAnnotation(leaf, "Indentation can't use both tabs and spaces");
          }
          else
          if (currentIndentLength > 0) {
            // If we don't have indent info registered
            if (indentInfo == null){
              indentInfo = currentIndent;
              lastIndent = currentIndentLength;
            } else {
              final Boolean useTab = indentInfo.useTab;
              final int indentInfoLength = indentInfo.length;
              // If indent and current indent use different space and tabs
              if (useTab && currentIndentText.contains(" ") || !useTab && currentIndentText.contains("\t")) {
                final String message = useTab
                                       ? "Inconsistent indentation: " + currentIndentLength +
                                         " spaces were used for indentation, but the rest of the document was indented using " +
                                         indentInfoLength + " tabs"
                                       : "Inconsistent indentation: " + currentIndentLength +
                                         " tabs were used for indentation, but the rest of the document was indented using " +
                                         indentInfoLength + " spaces";
                myHolder.createErrorAnnotation(leaf, message);
              } else {
                // Check indent length
                final int delta = currentIndentLength - lastIndent;
                if (currentIndentLength % indentInfoLength != 0 || delta > indentInfoLength) {
                  final String message = useTab
                                         ? currentIndentLength + " tabs were used for indentation. Must be indented using " + indentInfoLength + " tabs"
                                         : currentIndentLength + " spaces were used for indentation. Must be indented using " + indentInfoLength + " spaces";
                  myHolder.createErrorAnnotation(leaf, message);
                }
                lastIndent = currentIndentLength / indentInfoLength * indentInfoLength;
              }
            }
          }
        }
      }
      leaf = nextLeaf;
      nextLeaf = leaf != null ? PsiTreeUtil.nextLeaf(leaf) : null;
    }
  }


  private class IndentInfo {
    final int length;
    final boolean useTab;

    private IndentInfo(final int length, final boolean useTab) {
      this.length = length;
      this.useTab = useTab;
    }
  }


  private IndentInfo getIndent(final String text) {
    if (text.length() == 0){
      return new IndentInfo(0, false);
    }
    if (text.contains("\t ") || text.contains(" \t")){
      return new IndentInfo(-1, false);
    }
    return new IndentInfo(text.length(), text.contains("\t"));
  }
}
