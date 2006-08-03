package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateEditingAdapter;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.ide.util.MemberChooser;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.EditorHighlighter;
import com.intellij.openapi.editor.ex.HighlighterIterator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.java.IJavaElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;

import java.util.ArrayList;
import java.util.List;

public class DefaultInsertHandler implements InsertHandler,Cloneable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.completion.DefaultInsertHandler");

  protected static final Object EXPANDED_TEMPLATE_ATTR = Key.create("EXPANDED_TEMPLATE_ATTR");

  protected CompletionContext myContext;
  private int myStartOffset;
  private LookupData myLookupData;
  private LookupItem myLookupItem;

  private Project myProject;
  private PsiFile myFile;
  private Editor myEditor;
  protected Document myDocument;
  private InsertHandlerState myState;

  public void handleInsert(final CompletionContext context,
                           int startOffset, LookupData data, LookupItem item,
                           final boolean signatureSelected, final char completionChar) {
    DefaultInsertHandler delegate = this;

    if (isTemplateToBeCompleted(item)) {
      try {
        delegate = (DefaultInsertHandler)clone();
        delegate.clear();
      }
      catch (CloneNotSupportedException e) {
        e.printStackTrace();
      }
    }

    boolean toClear = true;
    try{
      toClear = delegate.handleInsertInner(context, startOffset, data, item, signatureSelected, completionChar);
    }
    finally{
      if (toClear) {
        delegate.clear();
      }
    }
  }

  private void clear() {
    myEditor = null;
    myDocument = null;
    myProject = null;
    myFile = null;
    myState = null;
    myLookupData = null;
    myLookupItem = null;
    myContext = null;
  }

  public boolean handleInsertInner(CompletionContext context,
                           int startOffset, LookupData data, LookupItem item,
                           final boolean signatureSelected, final char completionChar) {

    LOG.assertTrue(CommandProcessor.getInstance().getCurrentCommand() != null);
    PsiDocumentManager.getInstance(context.project).commitDocument(context.editor.getDocument());
    myContext = context;
    myStartOffset = startOffset;
    myLookupData = data;
    myLookupItem = item;

    myProject = myContext.project;
    myFile = myContext.file;
    myEditor = myContext.editor;
    myDocument = myEditor.getDocument();

    if (isTemplateToBeCompleted(myLookupItem)){
        handleTemplate(context, signatureSelected, completionChar);
      // we could not clear in this case since handleTemplate has templateFinished lisntener that works
      // with e.g. myLookupItem
        return false;
    }

    int tailType = getTailType(completionChar);

    adjustContextAfterLookupStringInsertion();
    myState = new InsertHandlerState(myContext.selectionEndOffset, myContext.selectionEndOffset);

    final boolean overwrite = completionChar != 0
      ? completionChar == Lookup.REPLACE_SELECT_CHAR
      : myLookupItem.getAttribute(LookupItem.OVERWRITE_ON_AUTOCOMPLETE_ATTR) != null;


    final boolean needLeftParenth = isToInsertParenth(tailType);
    final boolean hasParams = needLeftParenth && hasParams(signatureSelected);
    tailType = modifyTailTypeBasedOnMethodReturnType(signatureSelected, needLeftParenth, hasParams, tailType);

    if (overwrite)
      removeEndOfIdentifier(needLeftParenth && hasParams);
    else if(myContext.identifierEndOffset != myContext.selectionEndOffset)
      context.resetParensInfo();

    handleParenses(hasParams, needLeftParenth, tailType);
    handleBrackets();

    RangeMarker saveMaker = null;
    final boolean generateAnonymousBody = myLookupItem.getAttribute(LookupItem.GENERATE_ANONYMOUS_BODY_ATTR) != null;
    if (generateAnonymousBody){
      saveMaker = myDocument.createRangeMarker(myState.caretOffset, myState.caretOffset);
      myDocument.insertString(myState.tailOffset, "{}");
      myState.caretOffset = myState.tailOffset + 1;
      myState.tailOffset += 2;
    }

    myState.caretOffset = processTail(tailType, myState.caretOffset, myState.tailOffset);

    myEditor.getCaretModel().moveToOffset(myState.caretOffset);
    myEditor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    myEditor.getSelectionModel().removeSelection();

    try{
      myStartOffset = addImportForItem(myFile, myStartOffset, myLookupItem);
    }
    catch(IncorrectOperationException e){
      LOG.error(e);
    }

    if (needLeftParenth && hasParams){
      // Invoke parameters popup
      final PsiMethod method = myLookupItem.getObject() instanceof PsiMethod ? (PsiMethod)myLookupItem.getObject() : null;
      AutoPopupController.getInstance(myProject).autoPopupParameterInfo(myEditor, signatureSelected ? method : null);
    }

    if (tailType == TailType.DOT){
      AutoPopupController.getInstance(myProject).autoPopupMemberLookup(myEditor);
    }

    if (generateAnonymousBody){
      generateAnonymousBody();
      if (hasParams){
        int offset = saveMaker.getStartOffset();
        myEditor.getCaretModel().moveToOffset(offset);
        myEditor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
        myEditor.getSelectionModel().removeSelection();
      }
      return false;
    }

    if (insertingAnnotation()) {
      final Document document = context.editor.getDocument();
      PsiDocumentManager.getInstance(context.project).commitDocument(document);
      int expectedDogOffset = myStartOffset;

      PsiElement elementAt = myFile.findElementAt(expectedDogOffset - 1);
      final PsiJavaCodeReferenceElement javaCodeRef = PsiTreeUtil.getParentOfType(elementAt, PsiJavaCodeReferenceElement.class);

      if (javaCodeRef != null) {
        elementAt = myFile.findElementAt(
          (expectedDogOffset = javaCodeRef.getTextRange().getStartOffset()) - 1
        );
      }

      if(!"@".equals(elementAt.getText()) &&
         PsiTreeUtil.getParentOfType(elementAt,PsiImportStatement.class,PsiExpressionStatement.class) == null
        ) {
        document.insertString(expectedDogOffset, "@");
      }
    }
    return true;
  }

  private static boolean isTemplateToBeCompleted(final LookupItem lookupItem) {
    return lookupItem.getObject() instanceof Template
           && lookupItem.getAttribute(EXPANDED_TEMPLATE_ATTR) == null;
  }

  private int modifyTailTypeBasedOnMethodReturnType(boolean signatureSelected, boolean needLeftParenth, boolean hasParams, int tailType) {
    Object completion = myLookupItem.getObject();
    if(completion instanceof PsiMethod){
      final PsiMethod method = ((PsiMethod)completion);
      if (signatureSelected) {
        if(PsiType.VOID.equals(method.getReturnType()) && !(myContext.file instanceof PsiCodeFragment)) {
          tailType = TailType.SEMICOLON;
        }
      }
      else {
        final boolean hasOverloads = hasOverloads();
        if (!hasOverloads) {
          if(PsiType.VOID.equals(method.getReturnType()) && !(myContext.file instanceof PsiCodeFragment)) {
            tailType = TailType.SEMICOLON;
          }
        }

        // [dsl]todo[dsl,ven,ik]: need to write something better here
      }
    }
    return tailType;
  }

  private void adjustContextAfterLookupStringInsertion(){
    // Handling lookup auto insert
    myContext.shiftOffsets(myLookupItem.getLookupString().length() - myContext.getPrefix().length() - (myContext.selectionEndOffset - myContext.startOffset));
  }

  private void handleBrackets(){
    // brackets
    final Integer bracketsAttr = (Integer)myLookupItem.getAttribute(LookupItem.BRACKETS_COUNT_ATTR);
    if (bracketsAttr != null){
      int count = bracketsAttr.intValue();
      if(count > 0)
        myState.caretOffset = myState.tailOffset + 1;
      for(int i = 0; i < count; i++){
        myDocument.insertString(myState.tailOffset, "[]");
        myState.tailOffset += 2;
      }
    }
  }

  private void handleParenses(final boolean hasParams, final boolean needParenth, int tailType){
    final CodeInsightSettings settings = CodeInsightSettings.getInstance();
    final boolean myGenerateAnonymousBody = myLookupItem.getAttribute(LookupItem.GENERATE_ANONYMOUS_BODY_ATTR) != null;
    boolean insertRightParenth = !settings.INSERT_SINGLE_PARENTH
                                 || (settings.INSERT_DOUBLE_PARENTH_WHEN_NO_ARGS && !hasParams)
                                 || myGenerateAnonymousBody
                                 || (tailType != TailType.NONE && tailType != TailType.LPARENTH);

//    if(tailType == TailType.LPARENTH) tailType = TailType.NONE; //???
    if (needParenth){
      if (myContext.lparenthOffset >= 0){
        myState.tailOffset = myContext.argListEndOffset;
        if (myContext.rparenthOffset < 0 && insertRightParenth){
          myDocument.insertString(myState.tailOffset, ")");
          myState.tailOffset += 1;
          myContext.argListEndOffset = myState.tailOffset;
        }
        if (hasParams){
          myState.caretOffset = myContext.lparenthOffset + 1;
        }
        else{
          myState.caretOffset = myContext.argListEndOffset;
        }
      }
      else{
        final CodeStyleSettings styleSettings = CodeStyleSettingsManager.getSettings(myProject);
        myState.tailOffset = myContext.selectionEndOffset;
        myState.caretOffset = myContext.selectionEndOffset;

        if(styleSettings.SPACE_BEFORE_METHOD_CALL_PARENTHESES){
          myDocument.insertString(myState.tailOffset++, " ");
          myState.caretOffset ++;
        }
        if (insertRightParenth &&
            (tailType != TailType.CALL_RPARENTH || myGenerateAnonymousBody)
           ){
          myDocument.insertString(myState.tailOffset, "()");
          myState.tailOffset += 2;
          if (hasParams){
            myState.caretOffset++;
          }
          else{
            myState.caretOffset += 2;
          }
        }
        else{
          myDocument.insertString(myState.tailOffset++, "(");
          myState.caretOffset ++;
        }

        if(hasParams && styleSettings.SPACE_WITHIN_METHOD_CALL_PARENTHESES){
          myDocument.insertString(myState.caretOffset++, " ");
          myState.tailOffset++;
        }
      }
    }
  }

  private boolean isToInsertParenth(int tailType){
    boolean needParens = false;
    if (tailType == TailType.LPARENTH){
      needParens = true;
    }
    else if (myLookupItem.getObject() instanceof PsiMethod){
      PsiElement place = myFile.findElementAt(myStartOffset);
      if (myLookupItem.getObject() instanceof PsiAnnotationMethod) {
        if (place instanceof PsiIdentifier && (place.getParent() instanceof PsiNameValuePair
                                            || place.getParent().getParent() instanceof PsiNameValuePair)) return false;
      }
      needParens = place == null || !(place.getParent() instanceof PsiImportStaticReferenceElement);
    }
    else if (myLookupItem.getAttribute(LookupItem.NEW_OBJECT_ATTR) != null){
      PsiDocumentManager.getInstance(myProject).commitDocument(myDocument);
      needParens = true;
      final PsiClass aClass = (PsiClass)myLookupItem.getObject();

      PsiElement place = myFile.findElementAt(myStartOffset);

      if(myLookupItem.getAttribute(LookupItem.DONT_CHECK_FOR_INNERS) == null){
        PsiClass[] classes = aClass.getInnerClasses();
        for (PsiClass inner : classes) {
          if (!inner.hasModifierProperty(PsiModifier.STATIC)) continue;
          if (!inner.getManager().getResolveHelper().isAccessible(inner, place, null)) continue;
          needParens = false;
          break;
        }
      }
    } else if (insertingAnnotationWithParameters()) {
      needParens = true;
    }
    return needParens;
  }

  private boolean hasParams(boolean signatureSelected){
    boolean hasParms = false;
    if (myLookupItem.getObject() instanceof PsiMethod){
      final PsiMethod method = (PsiMethod)myLookupItem.getObject();
      hasParms = method.getParameterList().getParameters().length > 0;
      if (!signatureSelected){
        hasParms = hasParms || hasOverloads();
      }
    }
    else if (myLookupItem.getAttribute(LookupItem.NEW_OBJECT_ATTR) != null){
      PsiDocumentManager.getInstance(myProject).commitDocument(myDocument);
      final PsiClass aClass = (PsiClass)myLookupItem.getObject();

      final PsiElement place = myFile.findElementAt(myStartOffset);

      final PsiMethod[] constructors = aClass.getConstructors();
      for (PsiMethod constructor : constructors) {
        if (!aClass.getManager().getResolveHelper().isAccessible(constructor, place, null)) continue;
        if (constructor.getParameterList().getParameters().length > 0) {
          hasParms = true;
          break;
        }
      }
    }
    else {
      final String lookupString = myLookupItem.getLookupString();
      if (PsiKeyword.SYNCHRONIZED.equals(lookupString)) {
        final PsiElement place = myFile.findElementAt(myStartOffset);
        hasParms = PsiTreeUtil.getParentOfType(place, PsiMember.class, PsiCodeBlock.class) instanceof PsiCodeBlock;
      }
      else if(PsiKeyword.CATCH.equals(lookupString) ||
              PsiKeyword.SWITCH.equals(lookupString) ||
              PsiKeyword.WHILE.equals(lookupString) ||
              PsiKeyword.FOR.equals(lookupString))
        hasParms = true;
      else if (insertingAnnotationWithParameters()) {
        hasParms = true;
      }
    }
    return hasParms;
  }

  private boolean insertingAnnotationWithParameters() {
    if(insertingAnnotation()) {
      final Document document = myContext.editor.getDocument();
      PsiDocumentManager.getInstance(myContext.project).commitDocument(document);
      final PsiElement elementAt = myFile.findElementAt(myStartOffset - 1);

      if (PsiTreeUtil.getParentOfType(elementAt,PsiImportStatement.class) == null) {
        final PsiClass psiClass = (PsiClass)myLookupItem.getObject();
        for(PsiMethod m:psiClass.getMethods()) {
          if (!(m instanceof PsiAnnotationMethod)) continue;
          final PsiAnnotationMemberValue defaultValue = ((PsiAnnotationMethod)m).getDefaultValue();
          if (defaultValue == null) return true;
        }
      }
    }
    return false;
  }

  private boolean insertingAnnotation() {
    return myLookupItem.getObject() instanceof PsiClass &&
           ((PsiClass)myLookupItem.getObject()).isAnnotationType();
  }

  private boolean hasOverloads() {
    boolean hasParms = false;
    String name = ((PsiMethod)myLookupItem.getObject()).getName();
    for (LookupItem item1 : myLookupData.items) {
      if (myLookupItem == item1) continue;
      if (item1.getObject() instanceof PsiMethod) {
        String name1 = ((PsiMethod)item1.getObject()).getName();
        if (name1.equals(name)) {
          hasParms = true;
          break;
        }
      }
    }
    return hasParms;
  }

  protected void removeEndOfIdentifier(boolean needParenth){
    myContext.init();
    myDocument.deleteString(myContext.selectionEndOffset, myContext.identifierEndOffset);
    int shift = -(myContext.identifierEndOffset - myContext.selectionEndOffset);
    myContext.shiftOffsets(shift);
    myContext.selectionEndOffset = myContext.identifierEndOffset;
    if(myContext.lparenthOffset > 0 && !needParenth){
      myDocument.deleteString(myContext.lparenthOffset, myContext.argListEndOffset);
      myContext.resetParensInfo();
    }
  }

  private int getTailType(final char completionChar){
    final Integer attr = (Integer)myLookupItem.getAttribute(CompletionUtil.TAIL_TYPE_ATTR);
    int tailType = attr != null ? attr.intValue() : TailType.NONE;

    switch(completionChar){
      case '.':
        tailType = TailType.DOT;
        break;
      case ',':
        tailType = TailType.COMMA;
        break;
      case ';':
        tailType = TailType.SEMICOLON;
        break;
      case '=':
        tailType = TailType.EQ;
        break;
      case ' ':
        tailType = TailType.SPACE;
        break;
      case ':':
        tailType = TailType.CASE_COLON; //?
        break;
      case '(':
        tailType = TailType.LPARENTH;
        break;
      case '<':
        tailType = '<';
        break;
      case '>':
        tailType = '>';
        break;
      case '[':
        tailType = '[';
        break;
    }
    return tailType;
  }

  private void handleTemplate(final CompletionContext context, final boolean signatureSelected, final char completionChar){
    Template template = (Template)myLookupItem.getObject();

    int offset1 = context.startOffset;
    final RangeMarker offsetRangeMarker = context.editor.getDocument().createRangeMarker(offset1, offset1);

    TemplateManager.getInstance(myProject).startTemplate(
      context.editor,
      template,
      new TemplateEditingAdapter() {
        public void templateFinished(Template template) {
          myLookupItem.setAttribute(EXPANDED_TEMPLATE_ATTR, Boolean.TRUE);

          if (!offsetRangeMarker.isValid()) return;

          final int offset = offsetRangeMarker.getStartOffset();

          String lookupString =
              context.editor.getDocument().getCharsSequence().subSequence(
              offset,
              context.editor.getCaretModel().getOffset()).toString();
          myLookupItem.setLookupString(lookupString);

          CompletionContext newContext = new CompletionContext(context.project, context.editor, context.file, offset, offset);
          handleInsert(newContext, myStartOffset, myLookupData, myLookupItem, signatureSelected, completionChar);
        }
      }
    );
    return;
  }

  private int processTail(int tailType, int caretOffset, int tailOffset) {
    CodeStyleSettings styleSettings = CodeStyleSettingsManager.getSettings(myProject);
    int textLength = myDocument.getTextLength();
    CharSequence chars = myDocument.getCharsSequence();

    switch(tailType){
      case TailType.NONE:
        break;
      case TailType.SEMICOLON:
        if (tailOffset == textLength || chars.charAt(tailOffset) != ';'){
          myDocument.insertString(tailOffset, ";");
        }
        if (caretOffset == tailOffset){
          caretOffset++;
        }
        tailOffset++;
        break;
      case TailType.COMMA:
        if (styleSettings.SPACE_BEFORE_COMMA){
          if (tailOffset == textLength || chars.charAt(tailOffset) != ' '){
            myDocument.insertString(tailOffset, " ");
          }
          if (caretOffset == tailOffset){
            caretOffset++;
          }
          tailOffset++;
        }

        if (tailOffset == textLength || chars.charAt(tailOffset) != ','){
          myDocument.insertString(tailOffset, ",");
        }
        if (caretOffset == tailOffset){
          caretOffset++;
        }
        tailOffset++;

        if (styleSettings.SPACE_AFTER_COMMA){
          if (tailOffset == textLength || chars.charAt(tailOffset) != ' '){
            myDocument.insertString(tailOffset, " ");
          }
          if (caretOffset == tailOffset){
            caretOffset++;
          }
          tailOffset++;
        }
        break;

      case TailType.SPACE:
        if (tailOffset == textLength || chars.charAt(tailOffset) != ' '){
          myDocument.insertString(tailOffset, " ");
        }
        if (caretOffset == tailOffset){
          caretOffset++;
        }
        tailOffset++;
        break;

      case TailType.DOT:
        if (tailOffset == textLength || chars.charAt(tailOffset) != '.'){
          myDocument.insertString(tailOffset, ".");
        }
        if (caretOffset == tailOffset){
          caretOffset++;
        }
        tailOffset++;
        break;

      case TailType.CAST_RPARENTH:
        FeatureUsageTracker.getInstance().triggerFeatureUsed("editing.completion.smarttype.casting");
        // no breaks over here!!!
      case TailType.CALL_RPARENTH:
      case TailType.IF_RPARENTH:
      case TailType.WHILE_RPARENTH:
      case TailType.CALL_RPARENTH_SEMICOLON:
        caretOffset = processRparenthTail(tailType, caretOffset, tailOffset);
        break;

      case TailType.COND_EXPR_COLON:
        if (tailOffset < textLength - 1 && chars.charAt(tailOffset) == ' ' && chars.charAt(tailOffset + 1) == ':'){
          if (caretOffset == tailOffset){
            caretOffset += 2;
          }
          tailOffset += 2;
        }
        else if (tailOffset < textLength && chars.charAt(tailOffset) == ':'){
          if (caretOffset == tailOffset){
            caretOffset++;
          }
          tailOffset++;
        }
        else{
          myDocument.insertString(tailOffset, " : ");
          if (caretOffset == tailOffset){
            caretOffset += 3;
          }
          tailOffset += 3;
        }
        break;

      case TailType.EQ:
        if (tailOffset < textLength - 1 && chars.charAt(tailOffset) == ' ' && chars.charAt(tailOffset + 1) == '='){
          if (caretOffset == tailOffset){
            caretOffset += 2;
          }
          tailOffset += 2;
        }
        else if (tailOffset < textLength && chars.charAt(tailOffset) == '='){
          if (caretOffset == tailOffset){
            caretOffset++;
          }
          tailOffset++;
        }
        else{
          if (styleSettings.SPACE_AROUND_ASSIGNMENT_OPERATORS){
            myDocument.insertString(tailOffset, " =");
            textLength+=2;
            if (caretOffset == tailOffset){
              caretOffset += 2;
            }
            tailOffset += 2;
          }
          else{
            myDocument.insertString(tailOffset, "=");
            textLength++;
            if (caretOffset == tailOffset){
              caretOffset++;
            }
            tailOffset++;
          }
        }
        if (styleSettings.SPACE_AROUND_ASSIGNMENT_OPERATORS){
          if (tailOffset == textLength || chars.charAt(tailOffset) != ' '){
            myDocument.insertString(tailOffset, " ");
          }
          if (caretOffset == tailOffset){
            caretOffset++;
          }
          tailOffset++;
        }
        break;
      case TailType.CASE_COLON:
        if (tailOffset == textLength || chars.charAt(tailOffset) != ':'){
          myDocument.insertString(tailOffset, ":");
        }
        if (caretOffset == tailOffset){
          caretOffset++;
        }
        tailOffset++;
        break;

      default:
        if (tailOffset == textLength || chars.charAt(tailOffset) != tailType){
          myDocument.insertString(tailOffset, "" + (char)tailType);
        }
        if (caretOffset == tailOffset){
          caretOffset++;
        }
        tailOffset++;
        break;
      case TailType.UNKNOWN:
      case TailType.LPARENTH:
    }
    return caretOffset;
  }

  private int processRparenthTail(int tailType, int caretOffset, int tailOffset) {
    CodeStyleSettings styleSettings = CodeStyleSettingsManager.getSettings(myProject);
    CharSequence chars = myDocument.getCharsSequence();
    int textLength = myDocument.getTextLength();

    EditorHighlighter highlighter = ((EditorEx) myEditor).getHighlighter();

    int existingRParenthOffset = -1;
    for(HighlighterIterator iterator = highlighter.createIterator(tailOffset); !iterator.atEnd(); iterator.advance()){
      final IElementType tokenType = iterator.getTokenType();

      if (tokenType instanceof IJavaElementType && JavaTokenType.WHITE_SPACE_OR_COMMENT_BIT_SET.contains(tokenType) ||
          tokenType == TokenType.WHITE_SPACE) {
        continue;
      }

      if (tokenType == JavaTokenType.RPARENTH){
        existingRParenthOffset = iterator.getStart();
      }
      break;
    }

    if (existingRParenthOffset >= 0){
      PsiDocumentManager.getInstance(myProject).commitDocument(myDocument);
      TextRange range = getRangeToCheckParensBalance(myFile, myStartOffset);
      int balance = calcParensBalance(myDocument, highlighter, range.getStartOffset(), range.getEndOffset());
      if (balance > 0){
        existingRParenthOffset = -1;
      }
    }

    boolean spaceWithinParens;
    switch(tailType){
      case TailType.CALL_RPARENTH:
      case TailType.CALL_RPARENTH_SEMICOLON:
        spaceWithinParens = styleSettings.SPACE_WITHIN_METHOD_CALL_PARENTHESES;
        break;

      case TailType.IF_RPARENTH:
        spaceWithinParens = styleSettings.SPACE_WITHIN_IF_PARENTHESES;
        break;

      case TailType.WHILE_RPARENTH:
        spaceWithinParens = styleSettings.SPACE_WITHIN_WHILE_PARENTHESES;
        break;

      case TailType.CAST_RPARENTH:
        spaceWithinParens = styleSettings.SPACE_WITHIN_CAST_PARENTHESES;
        caretOffset = tailOffset;
        break;

      default:
        spaceWithinParens = false;
        LOG.assertTrue(false);
    }

    if (existingRParenthOffset < 0){
      if (spaceWithinParens){
        myDocument.insertString(tailOffset, " ");
        if (caretOffset == tailOffset){
          caretOffset++;
        }
        tailOffset++;
      }
      myDocument.insertString(tailOffset, ")");
      if (caretOffset == tailOffset){
        caretOffset++;
      }
      tailOffset++;
    }
    else{
      if (spaceWithinParens){
        if (tailOffset == existingRParenthOffset){
          myDocument.insertString(tailOffset, " ");
          if (caretOffset == tailOffset){
            caretOffset++;
          }
          tailOffset++;
          existingRParenthOffset++;
        }
      }
      if (caretOffset == tailOffset){
        caretOffset = existingRParenthOffset + 1;
      }
      tailOffset = existingRParenthOffset + 1;
    }

    if (tailType == TailType.CAST_RPARENTH && styleSettings.SPACE_AFTER_TYPE_CAST){
      if (tailOffset == textLength || chars.charAt(tailOffset) != ' '){
        myDocument.insertString(tailOffset, " ");
      }
      if (caretOffset == tailOffset){
        caretOffset++;
      }
      tailOffset++;
    }

    if (tailType == TailType.CALL_RPARENTH_SEMICOLON){
      if (tailOffset == textLength || chars.charAt(tailOffset) != ';'){
        myDocument.insertString(tailOffset, ";");
      }
      if (caretOffset == tailOffset){
        caretOffset++;
      }
      tailOffset++;
    }

    return caretOffset;
  }

  private static TextRange getRangeToCheckParensBalance(PsiFile file, int startOffset){
    PsiElement element = file.findElementAt(startOffset);
    PsiElement prevElement = element;
    element = element.getParent();
    while(true){
      if (!(element instanceof PsiExpression) &&
          !(element instanceof PsiExpressionList) &&
          !(element instanceof PsiJavaCodeReferenceElement) &&
          !(element instanceof PsiTypeElement)
      ){
        if (element instanceof PsiIfStatement){
          PsiIfStatement ifStatement = (PsiIfStatement)element;
          int start = ifStatement.getTextRange().getStartOffset();
          PsiStatement then = ifStatement.getThenBranch();
          int end = then != null ? then.getTextRange().getStartOffset() : ifStatement.getTextRange().getEndOffset();
          return new TextRange(start, end);
        }
        else { //TODO: other statements with '()'
          break;
        }
      }

      prevElement = element;
      element = element.getParent();
    }
    final int start = prevElement.getTextRange().getStartOffset();
    int end = prevElement.getTextRange().getEndOffset();
    PsiElement errorElement = prevElement.getNextSibling();

    while(errorElement instanceof PsiErrorElement){
      end += errorElement.getTextLength();
      while(errorElement.getNextSibling() == null && errorElement.getParent() != null)
        errorElement = errorElement.getParent();

      errorElement = errorElement.getNextSibling();
    }
    return new TextRange(start, end);
  }

  private static int calcParensBalance(Document document, EditorHighlighter highlighter, int rangeStart, int rangeEnd){
    LOG.assertTrue(0 <= rangeStart);
    LOG.assertTrue(rangeStart <= rangeEnd);
    LOG.assertTrue(rangeEnd <= document.getTextLength());

    HighlighterIterator iterator = highlighter.createIterator(rangeStart);
    int balance = 0;
    while(!iterator.atEnd() && iterator.getStart() < rangeEnd){
      IElementType tokenType = iterator.getTokenType();
      if (tokenType == JavaTokenType.LPARENTH){
        balance++;
      }
      else if (tokenType == JavaTokenType.RPARENTH){
        balance--;
      }
      iterator.advance();
    }
    return balance;
  }

  private void generateAnonymousBody(){
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    int offset = myEditor.getCaretModel().getOffset();
    PsiElement element = myFile.findElementAt(offset);
    if (element == null) return;
    if (element.getParent() instanceof PsiAnonymousClass){
      try{
        CodeStyleManager.getInstance(myProject).reformat(element.getParent());
      }
      catch(IncorrectOperationException e){
        LOG.error(e);
      }
      offset = element.getParent().getTextRange().getEndOffset() - 1;
      myEditor.getCaretModel().moveToOffset(offset);
      myEditor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
      myEditor.getSelectionModel().removeSelection();
    }
    final SmartPsiElementPointer pointer = SmartPointerManager.getInstance(myProject).createSmartPsiElementPointer(element);
    ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run(){
          CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
            public void run() {
              if (myProject.isDisposed()) return;

              PsiDocumentManager.getInstance(myProject).commitDocument(myDocument);
              PsiElement element = pointer.getElement();
              if (element == null) return;

              while(true){
                if (element instanceof PsiFile) return;
                PsiElement parent = element.getParent();
                if (parent instanceof PsiAnonymousClass) break;
                element = parent;
              }
              final PsiAnonymousClass aClass = (PsiAnonymousClass)element.getParent();

              final CandidateInfo[] candidatesToImplement = OverrideImplementUtil.getMethodsToOverrideImplement(aClass, true);
              boolean invokeOverride = candidatesToImplement.length == 0;
              if (invokeOverride){
                chooseAndOverrideMethodsInAdapter(myProject, myEditor, aClass);
              }
              else{
                ApplicationManager.getApplication().runWriteAction(new Runnable() {
                  public void run() {
                    try{
                      PsiMethod[] prototypes = OverrideImplementUtil.overrideOrImplementMethods(aClass, candidatesToImplement, false, false);
                      Object[] resultMembers = GenerateMembersUtil.insertMembersBeforeAnchor(aClass, null, prototypes);
                      GenerateMembersUtil.positionCaret(myEditor, (PsiElement)resultMembers[0], true);
                    }
                    catch(IncorrectOperationException ioe){
                      LOG.error(ioe);
                    }
                  }
                });
              }

              clear();
            }
          }, CompletionBundle.message("completion.smart.type.generate.anonymous.body"), null);
        }
      });
  }

  private static void chooseAndOverrideMethodsInAdapter(final Project project, final Editor editor, final PsiAnonymousClass aClass) {
    PsiClass baseClass = aClass.getBaseClassType().resolve();
    if (baseClass == null) return;
    PsiMethod[] allBaseMethods = baseClass.getMethods();
    if(allBaseMethods.length == 0) return;

    List<CandidateInfo> methods = new ArrayList<CandidateInfo>();
    for (final PsiMethod method : allBaseMethods) {
      if (OverrideImplementUtil.isOverridable(method)) {
        methods.add(new CandidateInfo(method, PsiSubstitutor.UNKNOWN));
      }
    }

    boolean isJdk15Enabled = LanguageLevel.JDK_1_5.compareTo(PsiUtil.getLanguageLevel(aClass)) <= 0;
    final MemberChooser chooser = new MemberChooser(methods.toArray(), false, true, project, isJdk15Enabled);
    chooser.setTitle(CompletionBundle.message("completion.smarttype.select.methods.to.override"));
    chooser.setCopyJavadocVisible(true);

    chooser.show();
    Object[] selectedElements = chooser.getSelectedElements();
    if (selectedElements == null || selectedElements.length == 0) return;

    CandidateInfo[] selectedCandidates = new CandidateInfo[selectedElements.length];

    try{
      System.arraycopy(selectedElements, 0, selectedCandidates, 0, selectedCandidates.length);
      final PsiMethod[] prototypes = OverrideImplementUtil.overrideOrImplementMethods(aClass, selectedCandidates, chooser.isCopyJavadoc(), chooser.isInsertOverrideAnnotation());

      for (PsiMethod prototype : prototypes) {
        PsiStatement[] statements = prototype.getBody().getStatements();
        if (statements.length > 0 && prototype.getReturnType() == PsiType.VOID) {
          statements[0].delete(); // remove "super(..)" call
        }
      }

      final int offset = editor.getCaretModel().getOffset();
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          try{
            Object[] resultMembers = GenerateMembersUtil.insertMembersAtOffset(aClass.getContainingFile(), offset, prototypes);
            GenerateMembersUtil.positionCaret(editor, (PsiElement)resultMembers[0], true);
          }
          catch(IncorrectOperationException e){
            LOG.error(e);
          }
        }
      });
    }
    catch(IncorrectOperationException ioe){
      LOG.error(ioe);
    }
  }

  private static int addImportForItem(PsiFile file, int startOffset, LookupItem item) throws IncorrectOperationException {
    PsiDocumentManager.getInstance(file.getProject()).commitAllDocuments();

    Object o = item.getObject();
    if (o instanceof PsiClass){
      PsiClass aClass = (PsiClass)o;
      int length = aClass.getName().length();
      final int newOffset = addImportForClass(file, startOffset, startOffset + length, aClass);
      shortenReference(file, newOffset);
      return newOffset;
    }
    else if (o instanceof PsiType){
      PsiType type = ((PsiType)o).getDeepComponentType();
      if (type instanceof PsiClassType) {
        PsiClass refClass = ((PsiClassType) type).resolve();
        if (refClass != null){
          int length = refClass.getName().length();
          return addImportForClass(file, startOffset, startOffset + length, refClass);
        }
      }
    }
    else if (o instanceof PsiMethod){
      PsiMethod method = (PsiMethod)o;
      if (method.isConstructor()){
        PsiClass aClass = (PsiClass)item.getAttribute(LookupItem.CONTAINING_CLASS_ATTR);
        if (aClass == null){
          aClass = method.getContainingClass();
        }
        if (aClass != null){
          int length = method.getName().length();
          return addImportForClass(file, startOffset, startOffset + length, aClass);
        }
      }
    }

    return startOffset;
  }

  //need to call to shorten references in type argument list
  private static void shortenReference(final PsiFile file, final int offset) throws IncorrectOperationException {
    final PsiReference ref = file.findReferenceAt(offset);
    if (ref instanceof PsiJavaCodeReferenceElement) {
      file.getManager().getCodeStyleManager().shortenClassReferences(((PsiJavaCodeReferenceElement)ref));
    }
  }

  private static int addImportForClass(PsiFile file, int startOffset, int endOffset, PsiClass aClass) throws IncorrectOperationException {
    SmartPsiElementPointer pointer = SmartPointerManager.getInstance(file.getProject()).createSmartPsiElementPointer(aClass);
    LOG.assertTrue(CommandProcessor.getInstance().getCurrentCommand() != null);
    LOG.assertTrue(ApplicationManager.getApplication().getCurrentWriteAction(null) != null);

    final PsiManager manager = file.getManager();
    final PsiResolveHelper helper = manager.getResolveHelper();

    final Document document = PsiDocumentManager.getInstance(manager.getProject()).getDocument(file);

    CharSequence chars = document.getCharsSequence();
    int length = document.getTextLength();
    int newStartOffset = startOffset;

    PsiElement element = file.findElementAt(startOffset);
    String refText = chars.subSequence(startOffset, endOffset).toString();
    PsiClass refClass = helper.resolveReferencedClass(refText, element);
    if (refClass != null && (refClass.getQualifiedName() == null/* local classes and parameters*/
                             || manager.areElementsEquivalent(aClass, refClass))) return newStartOffset;
    boolean insertSpace = endOffset < length && Character.isJavaIdentifierPart(chars.charAt(endOffset));

    if (insertSpace){
      document.insertString(endOffset, " ");
    }
    String name = aClass.getName();
    document.replaceString(startOffset, endOffset, name);
    endOffset = startOffset + name.length();

    PsiDocumentManager.getInstance(manager.getProject()).commitAllDocuments();

    element = file.findElementAt(startOffset);
    if (element instanceof PsiIdentifier){
      PsiElement parent = element.getParent();
      if (parent instanceof PsiJavaCodeReferenceElement && !((PsiJavaCodeReferenceElement)parent).isQualified()){
        PsiJavaCodeReferenceElement ref = (PsiJavaCodeReferenceElement)parent;
        final PsiElement pointerElement = pointer.getElement();
        if(pointerElement instanceof PsiClass){
          if (!(ref instanceof PsiImportStaticReferenceElement)) {
            PsiJavaCodeReferenceElement newRef = (PsiJavaCodeReferenceElement)ref.bindToElement(pointerElement);
            newRef = CodeInsightUtil.forcePsiPostprocessAndRestoreElement(newRef);
            final TextRange textRange = newRef.getTextRange();
            endOffset = textRange.getEndOffset();
            newStartOffset = textRange.getStartOffset();
          }
          else {
            PsiImportStaticStatement statement = ((PsiImportStaticReferenceElement)ref).bindToTargetClass((PsiClass) pointerElement);
            statement = CodeInsightUtil.forcePsiPostprocessAndRestoreElement(statement);
            final TextRange textRange = statement.getTextRange();
            endOffset = textRange.getEndOffset();
            newStartOffset = textRange.getStartOffset();
          }
        }
      }
    }

    if (insertSpace){
      document.deleteString(endOffset, endOffset + 1);
    }

    return newStartOffset;
  }

  private static class InsertHandlerState{
    int tailOffset;
    int caretOffset;

    public InsertHandlerState(int caretOffset, int tailOffset){
      this.caretOffset = caretOffset;
      this.tailOffset = tailOffset;
    }
  }
}
