package com.intellij.codeInsight.completion;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.html.HtmlTag;
import com.intellij.psi.javadoc.PsiDocToken;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlToken;
import com.intellij.psi.xml.XmlTokenType;
import org.apache.oro.text.regex.Pattern;
import org.apache.oro.text.regex.Perl5Matcher;

public class CompletionContext implements Cloneable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.completion.CompletionContext");
  private Pattern myPattern;
  private Perl5Matcher myMatcher;

  protected Object clone() {
    try {
      return super.clone();
    }
    catch (CloneNotSupportedException e) {
      LOG.error(e);
      return null;
    }
  }

  public final Project project;
  public final Editor editor;
  public final PsiFile file;
  public int startOffset;
  public int selectionEndOffset;
  public int offset;

  public int identifierEndOffset = -1;
  public int lparenthOffset = -1;
  public int rparenthOffset = -1;
  public int argListEndOffset = -1;
  public boolean hasArgs = false;

  private String myPrefix = "";

  public CompletionContext(Project project, Editor editor, PsiFile file, int offset1, int offset2){
    this.project = project;
    this.editor = editor;
    this.file = file;

    startOffset = offset1;
    selectionEndOffset = offset2;

    init();
  }

  public void init(){
    identifierEndOffset = selectionEndOffset;

    PsiElement element = file.findElementAt(selectionEndOffset);
    if (element == null) return;

    final PsiReference reference = file.findReferenceAt(selectionEndOffset);
    if(reference != null){
      if(reference instanceof PsiJavaCodeReferenceElement){
        identifierEndOffset = element.getParent().getTextRange().getEndOffset();
      }
      else{
        identifierEndOffset = reference.getElement().getTextRange().getStartOffset() + reference.getRangeInElement().getEndOffset();
      }

      element = file.findElementAt(identifierEndOffset);
    }
    else if (isWord(element)){
      if(element instanceof PsiIdentifier && element.getParent() instanceof PsiJavaCodeReferenceElement){
        identifierEndOffset = element.getParent().getTextRange().getEndOffset();
      }
      else{
        identifierEndOffset = element.getTextRange().getEndOffset();
      }

      element = file.findElementAt(identifierEndOffset);
      if (element == null) return;
    }

    if (element instanceof PsiWhiteSpace &&
        ( !element.textContains('\n') ||
          CodeStyleSettingsManager.getInstance(project).getCurrentSettings().METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE
        )
       ){
      element = file.findElementAt(element.getTextRange().getEndOffset());
    }

    if (element instanceof PsiJavaToken
        && ((PsiJavaToken)element).getTokenType() == JavaTokenType.LPARENTH){

      if(element.getParent() instanceof PsiExpressionList || ".".equals(file.findElementAt(selectionEndOffset - 1).getText())){
        lparenthOffset = element.getTextRange().getStartOffset();
        PsiElement list = element.getParent();
        PsiElement last = list.getLastChild();
        if (last instanceof PsiJavaToken && ((PsiJavaToken)last).getTokenType() == JavaTokenType.RPARENTH){
          rparenthOffset = last.getTextRange().getStartOffset();
        }
        argListEndOffset = list.getTextRange().getEndOffset();
        if(element instanceof PsiExpressionList)
          hasArgs = ((PsiExpressionList)element).getExpressions().length > 0;
      }
    }
  }
  private static boolean isWord(PsiElement element) {
    if (element instanceof PsiIdentifier){
      return true;
    }
    else if (element instanceof PsiKeyword){
      return true;
    }
    else if (element instanceof PsiJavaToken){
      final String text = element.getText();
      if(PsiKeyword.TRUE.equals(text)) return true;
      if(PsiKeyword.FALSE.equals(text)) return true;
      if(PsiKeyword.NULL.equals(text)) return true;
      return false;
    }
    else if (element instanceof PsiDocToken) {
      IElementType tokenType = ((PsiDocToken)element).getTokenType();
      return tokenType == JavaDocTokenType.DOC_TAG_VALUE_TOKEN || tokenType == JavaDocTokenType.DOC_TAG_NAME;
    }
    else if (element instanceof XmlToken) {
      IElementType tokenType = ((XmlToken)element).getTokenType();
      return tokenType == XmlTokenType.XML_TAG_NAME ||
          tokenType == XmlTokenType.XML_NAME ||
          tokenType == XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN ||
          // html data chars contains whitespaces
          (tokenType == XmlTokenType.XML_DATA_CHARACTERS && !(element.getParent() instanceof HtmlTag));
    }
    else{
      return false;
    }
  }

  public void shiftOffsets(int shift){
    if (shift != 0){
      selectionEndOffset += shift; //?
      identifierEndOffset += shift;
      if (lparenthOffset >= 0){
        lparenthOffset += shift;
      }
      if (rparenthOffset >= 0){
        rparenthOffset += shift;
      }
      if (argListEndOffset >= 0){
        argListEndOffset += shift;
      }
    }
  }

  public void resetParensInfo(){
    rparenthOffset = -1;
    argListEndOffset = -1;
    identifierEndOffset = -1;
    lparenthOffset = -1;
  }

  public boolean prefixMatches(final String name) {
    if (myPattern == null) {
      myPattern = CompletionUtil.createCamelHumpsMatcher(myPrefix);
      myMatcher = new Perl5Matcher();
    }

    return myMatcher.matches(name, myPattern);
  }

  public String getPrefix() {
    return myPrefix;
  }

  public void setPrefix(final String prefix) {
    myPattern = null;
    myMatcher = null;

    myPrefix = prefix;
  }
}

