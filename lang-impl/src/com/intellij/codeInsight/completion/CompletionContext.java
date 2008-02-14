package com.intellij.codeInsight.completion;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.html.HtmlTag;
import com.intellij.psi.javadoc.PsiDocToken;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlToken;
import com.intellij.psi.xml.XmlTokenType;
import org.jetbrains.annotations.Nullable;

public class CompletionContext {
  public static final Key<CompletionContext> COMPLETION_CONTEXT_KEY = Key.create("CompletionContext");
  private static final Key START_OFFSET = Key.create("start");
  private static final Key SEL_END_OFFSET = Key.create("selEnd");
  private static final Key ID_END_OFFSET = Key.create("idEnd");
  private static final Key LPAREN_OFFSET = Key.create("lparen");
  private static final Key RPAREN_OFFSET = Key.create("rparen");
  private static final Key ARG_LIST_END_OFFSET = Key.create("argListEnd");

  public final Project project;
  public final Editor editor;
  public final PsiFile file;
  public boolean hasArgs = false;
  private OffsetMap myOffsetMap;

  private String myPrefix = "";

  public CompletionContext(Project project, Editor editor, PsiFile file, int offset1, int offset2){
    this.project = project;
    this.editor = editor;
    this.file = file;
    myOffsetMap = new OffsetMap(editor.getDocument());


    setStartOffset(offset1);
    setSelectionEndOffset(offset2);

    init();
  }

  public void init(){
    setIdentifierEndOffset(getSelectionEndOffset());

    PsiElement element = file.findElementAt(getSelectionEndOffset());
    if (element == null) return;

    final PsiReference reference = file.findReferenceAt(getSelectionEndOffset());
    if(reference != null){
      if(reference instanceof PsiJavaCodeReferenceElement){
        setIdentifierEndOffset(element.getParent().getTextRange().getEndOffset());
      }
      else{
        setIdentifierEndOffset(reference.getElement().getTextRange().getStartOffset() + reference.getRangeInElement().getEndOffset());
      }

      element = file.findElementAt(getIdentifierEndOffset());
    }
    else if (isWord(element)){
      if(element instanceof PsiIdentifier && element.getParent() instanceof PsiJavaCodeReferenceElement){
        setIdentifierEndOffset(element.getParent().getTextRange().getEndOffset());
      }
      else{
        setIdentifierEndOffset(element.getTextRange().getEndOffset());
      }

      element = file.findElementAt(getIdentifierEndOffset());
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

      if(element.getParent() instanceof PsiExpressionList || ".".equals(file.findElementAt(getSelectionEndOffset() - 1).getText())
        || PlatformPatterns.psiElement().afterLeaf(PlatformPatterns.psiElement(JavaTokenType.NEW_KEYWORD)).accepts(element)) {
        setLparenthOffset(element.getTextRange().getStartOffset());
        PsiElement list = element.getParent();
        PsiElement last = list.getLastChild();
        if (last instanceof PsiJavaToken && ((PsiJavaToken)last).getTokenType() == JavaTokenType.RPARENTH){
          setRparenthOffset(last.getTextRange().getStartOffset());
        }



        setArgListEndOffset(list.getTextRange().getEndOffset());
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
  }

  public void resetParensInfo(){
    myOffsetMap.removeOffset(LPAREN_OFFSET);
    myOffsetMap.removeOffset(RPAREN_OFFSET);
    myOffsetMap.removeOffset(ARG_LIST_END_OFFSET);
    myOffsetMap.removeOffset(ID_END_OFFSET);
  }

  public String getPrefix() {
    return myPrefix;
  }

  public void setPrefix(PsiElement insertedElement, int offset, @Nullable CompletionData completionData) {
    setPrefix(completionData == null ? CompletionData.findPrefixStatic(insertedElement, offset) : completionData.findPrefix(insertedElement, offset));
  }

  public void setPrefix(final String prefix) {
    myPrefix = prefix;
  }

  public int getStartOffset() {
    return myOffsetMap.getOffset(START_OFFSET);
  }

  public void setStartOffset(final int newStartOffset) {
    myOffsetMap.addOffset(START_OFFSET, newStartOffset, false);
  }

  public int getSelectionEndOffset() {
    return myOffsetMap.getOffset(SEL_END_OFFSET);
  }

  public void setSelectionEndOffset(final int selectionEndOffset) {
    myOffsetMap.addOffset(SEL_END_OFFSET, selectionEndOffset, true);
  }

  public int getIdentifierEndOffset() {
    return myOffsetMap.getOffset(ID_END_OFFSET);
  }

  public void setIdentifierEndOffset(final int identifierEndOffset) {
    myOffsetMap.addOffset(ID_END_OFFSET, identifierEndOffset, true);
  }

  public int getLparenthOffset() {
    return myOffsetMap.getOffset(LPAREN_OFFSET);
  }

  public void setLparenthOffset(final int lparenthOffset) {
    myOffsetMap.addOffset(LPAREN_OFFSET, lparenthOffset, true);
  }

  public int getRparenthOffset() {
    return myOffsetMap.getOffset(RPAREN_OFFSET);
  }

  public void setRparenthOffset(final int rparenthOffset) {
    myOffsetMap.addOffset(RPAREN_OFFSET, rparenthOffset, true);
  }

  public int getArgListEndOffset() {
    return myOffsetMap.getOffset(ARG_LIST_END_OFFSET);
  }

  public void setArgListEndOffset(final int argListEndOffset) {
    myOffsetMap.addOffset(ARG_LIST_END_OFFSET, argListEndOffset, true);
  }
}

