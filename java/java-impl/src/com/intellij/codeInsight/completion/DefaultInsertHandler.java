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
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.*;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class DefaultInsertHandler extends TemplateInsertHandler implements Cloneable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.completion.DefaultInsertHandler");

  protected InsertionContext myContext;
  private LookupItem<?> myLookupItem;

  private Project myProject;
  private PsiFile myFile;
  private Editor myEditor;
  protected Document myDocument;
  private InsertHandlerState myState;

  public void handleInsert(final InsertionContext context, LookupElement item) {
    super.handleInsert(context, item);

    handleInsertInner(context, (LookupItem)item, context.getCompletionChar());
  }

  private void handleInsertInner(InsertionContext context, LookupItem item, final char completionChar) {
    LOG.assertTrue(CommandProcessor.getInstance().getCurrentCommand() != null);
    PsiDocumentManager.getInstance(context.getProject()).commitDocument(context.getEditor().getDocument());
    myContext = context;
    myLookupItem = item;

    myProject = myContext.getProject();
    myFile = myContext.getFile();
    myEditor = myContext.getEditor();
    myDocument = myEditor.getDocument();

    TailType tailType = getTailType(completionChar);
    if (completionChar == Lookup.COMPLETE_STATEMENT_SELECT_CHAR) {
      //tailType = TailType.SMART_COMPLETION;
    }
    if (myLookupItem.getAttribute(LookupItem.NEW_OBJECT_ATTR) != null && completionChar == '(') {
      tailType = TailType.NONE;
    }

    myState = new InsertHandlerState(myContext.getSelectionEndOffset(), myContext.getSelectionEndOffset());

    final boolean needLeftParenth = isToInsertParenth();
    final boolean hasParams = needLeftParenth && hasParams();

    if (CompletionUtil.isOverwrite(item, completionChar))
      removeEndOfIdentifier(needLeftParenth && hasParams);
    else if(myContext.getOffsetMap().getOffset(CompletionInitializationContext.IDENTIFIER_END_OFFSET) != myContext.getSelectionEndOffset())
      JavaCompletionUtil.resetParensInfo(context.getOffsetMap());

    handleParenses(hasParams, needLeftParenth, tailType);
    handleBrackets();

    if (myLookupItem.getObject() instanceof PsiVariable) {
      if (completionChar == '!' && PsiType.BOOLEAN.isAssignableFrom(((PsiVariable) myLookupItem.getObject()).getType())) {
        PsiDocumentManager.getInstance(myProject).commitDocument(myDocument);
        final PsiReferenceExpression ref =
            PsiTreeUtil.findElementOfClassAtOffset(myFile, myState.tailOffset - 1, PsiReferenceExpression.class, false);
        if (ref != null) {
          FeatureUsageTracker.getInstance().triggerFeatureUsed(CodeCompletionFeatures.EXCLAMATION_FINISH);
          myDocument.insertString(ref.getTextRange().getStartOffset(), "!");
          myState.caretOffset++;
          myState.tailOffset++;
        }
      }
    }

    RangeMarker saveMaker = null;
    final boolean generateAnonymousBody = myLookupItem.getAttribute(LookupItem.GENERATE_ANONYMOUS_BODY_ATTR) != null;
    if (generateAnonymousBody){
      saveMaker = myDocument.createRangeMarker(myState.caretOffset, myState.caretOffset);
      myDocument.insertString(myState.tailOffset, "{}");
      myState.caretOffset = myState.tailOffset + 1;
      myState.tailOffset += 2;
    }

    myContext.setTailOffset(myState.tailOffset);
    myState.caretOffset = processTail(tailType, myState.caretOffset, myState.tailOffset);
    myEditor.getSelectionModel().removeSelection();

    qualifyIfNeeded();


    if (needLeftParenth && hasParams){
      // Invoke parameters popup
      AutoPopupController.getInstance(myProject).autoPopupParameterInfo(myEditor, null);
    }

    if (tailType == TailType.DOT){
      AutoPopupController.getInstance(myProject).autoPopupMemberLookup(myEditor, null);
    }

    if (generateAnonymousBody) {
      if (hasParams) {
        int offset = saveMaker.getStartOffset();
        myEditor.getCaretModel().moveToOffset(offset);
        myEditor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
        myEditor.getSelectionModel().removeSelection();
      }
      return;
    }

    if (completionChar == '#') {
      context.setLaterRunnable(new Runnable() {
        public void run() {
           new CodeCompletionHandlerBase(CompletionType.BASIC) {
           }.invoke(myProject, myEditor, myFile);
        }
      });
    }

    if (insertingAnnotation()) {
      // Check if someone inserts annotation class that require @
      PsiElement elementAt = myFile.findElementAt(myContext.getStartOffset());
      final PsiElement parentElement = elementAt != null ? elementAt.getParent():null;

      if (elementAt instanceof PsiIdentifier &&
          (PsiTreeUtil.getParentOfType(elementAt, PsiAnnotationParameterList.class) != null ||
           parentElement instanceof PsiErrorElement && parentElement.getParent() instanceof PsiJavaFile // top level annotation without @
          )
          && isAtTokenNeeded()) {
        int expectedOffsetForAtToken = elementAt.getTextRange().getStartOffset();
        myDocument.insertString(expectedOffsetForAtToken, "@");
      }
    }
  }

  private void qualifyIfNeeded() {
    try{
      if (myLookupItem.getObject() instanceof PsiField) {
        PsiDocumentManager.getInstance(myFile.getProject()).commitAllDocuments();
        PsiReference reference = myFile.findReferenceAt(myContext.getStartOffset());
        if (reference instanceof PsiReferenceExpression && !((PsiReferenceExpression) reference).isQualified()) {
          final PsiField member = (PsiField)myLookupItem.getObject();
          final PsiVariable target =
              JavaPsiFacade.getInstance(myProject).getResolveHelper().resolveReferencedVariable(member.getName(), (PsiElement)reference);
          if (member.getManager().areElementsEquivalent(target, JavaCompletionUtil.getOriginalElement(member))) return;
          
          final PsiClass psiClass = member.getContainingClass();
          if (psiClass != null && StringUtil.isNotEmpty(psiClass.getName())) {
            myDocument.insertString(myContext.getStartOffset(), psiClass.getName() + ".");
          }
        }
      }
      addImportForItem(myFile, myContext.getStartOffset(), myLookupItem);
      if (myContext.getTailOffset() < 0) {  //hack, hack, hack. ideally the tail offset just should survive after the importing stuff
        myContext.setTailOffset(myEditor.getCaretModel().getOffset());
      }
    }
    catch(IncorrectOperationException e){
      LOG.error(e);
    }                                                                       
  }

  private boolean isAtTokenNeeded() {
    HighlighterIterator iterator = ((EditorEx)myContext.getEditor()).getHighlighter().createIterator(myContext.getStartOffset());
    LOG.assertTrue(iterator.getTokenType() == JavaTokenType.IDENTIFIER);
    iterator.retreat();
    if (iterator.getTokenType() == TokenType.WHITE_SPACE) iterator.retreat();
    return iterator.getTokenType() != JavaTokenType.AT && iterator.getTokenType() != JavaTokenType.DOT;
  }

  private void handleBrackets(){
    // brackets
    final Integer bracketsAttr = (Integer)myLookupItem.getUserData(LookupItem.BRACKETS_COUNT_ATTR);
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

  private void handleParenses(final boolean hasParams, final boolean needParenth, TailType tailType){
    final boolean generateAnonymousBody = myLookupItem.getAttribute(LookupItem.GENERATE_ANONYMOUS_BODY_ATTR) != null;
    boolean insertRightParenth = tailType != TailType.SMART_COMPLETION;

    if (needParenth){
      if (myContext.getOffsetMap().getOffset(JavaCompletionUtil.LPAREN_OFFSET) >= 0 && myContext.getOffsetMap().getOffset(JavaCompletionUtil.ARG_LIST_END_OFFSET) >= 0){
        myState.tailOffset = myContext.getOffsetMap().getOffset(JavaCompletionUtil.ARG_LIST_END_OFFSET);
        if (myContext.getOffsetMap().getOffset(JavaCompletionUtil.RPAREN_OFFSET) < 0 && insertRightParenth){
          myDocument.insertString(myState.tailOffset, ")");
          myState.tailOffset += 1;
        }
        if (hasParams){
          myState.caretOffset = myContext.getOffsetMap().getOffset(JavaCompletionUtil.LPAREN_OFFSET) + 1;
        }
        else{
          myState.caretOffset = myContext.getOffsetMap().getOffset(JavaCompletionUtil.ARG_LIST_END_OFFSET);
        }
      }
      else{
        final CodeStyleSettings styleSettings = CodeStyleSettingsManager.getSettings(myProject);
        myState.tailOffset = myContext.getSelectionEndOffset();
        myState.caretOffset = myContext.getSelectionEndOffset();

        if(styleSettings.SPACE_BEFORE_METHOD_CALL_PARENTHESES){
          myDocument.insertString(myState.tailOffset++, " ");
          myState.caretOffset ++;
        }
        if (insertRightParenth) {
          final CharSequence charsSequence = myDocument.getCharsSequence();
          if (charsSequence.length() <= myState.tailOffset || charsSequence.charAt(myState.tailOffset) != '(') {
            myDocument.insertString(myState.tailOffset, "(");
          }

          myDocument.insertString(myState.tailOffset + 1, ")");
          if (hasParams){
            myState.tailOffset += 2;
            myState.caretOffset++;
          }
          else{
            if (tailType != TailTypes.CALL_RPARENTH || generateAnonymousBody) {
              myState.tailOffset += 2;
              myState.caretOffset += 2;
            }
            else {
              myState.tailOffset++;
              myState.caretOffset++;
            }
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

  private boolean isToInsertParenth(){
    boolean needParens = false;
    if (myLookupItem.getAttribute(LookupItem.NEW_OBJECT_ATTR) != null){
      PsiDocumentManager.getInstance(myProject).commitDocument(myDocument);
      needParens = true;
      final PsiClass aClass = (PsiClass)myLookupItem.getObject();

      PsiElement place = myFile.findElementAt(myContext.getStartOffset());

      if(myLookupItem.getAttribute(LookupItem.DONT_CHECK_FOR_INNERS) == null){
        PsiClass[] classes = aClass.getInnerClasses();
        for (PsiClass inner : classes) {
          if (!inner.hasModifierProperty(PsiModifier.STATIC)) continue;
          if (!JavaPsiFacade.getInstance(inner.getProject()).getResolveHelper().isAccessible(inner, place, null)) continue;
          needParens = false;
          break;
        }
      }
    } else if (insertingAnnotationWithParameters()) {
      needParens = true;
    }
    return needParens;
  }

  private boolean hasParams(){
    boolean hasParms = false;
    if (myLookupItem.getAttribute(LookupItem.NEW_OBJECT_ATTR) != null){
      PsiDocumentManager.getInstance(myProject).commitDocument(myDocument);
      final PsiClass aClass = (PsiClass)myLookupItem.getObject();

      final PsiElement place = myFile.findElementAt(myContext.getStartOffset());

      final PsiMethod[] constructors = aClass.getConstructors();
      for (PsiMethod constructor : constructors) {
        if (!JavaPsiFacade.getInstance(aClass.getProject()).getResolveHelper().isAccessible(constructor, place, null)) continue;
        if (constructor.getParameterList().getParametersCount() > 0) {
          hasParms = true;
          break;
        }
      }
    }
    else {
      final String lookupString = myLookupItem.getLookupString();
      if (PsiKeyword.SYNCHRONIZED.equals(lookupString)) {
        final PsiElement place = myFile.findElementAt(myContext.getStartOffset());
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
      final Document document = myContext.getEditor().getDocument();
      PsiDocumentManager.getInstance(myContext.getProject()).commitDocument(document);
      PsiElement elementAt = myFile.findElementAt(myContext.getStartOffset());
      if (elementAt instanceof PsiIdentifier) {
        final PsiModifierListOwner parent = PsiTreeUtil.getParentOfType(elementAt, PsiModifierListOwner.class, false, PsiCodeBlock.class);
        if (parent != null) {
          for (PsiMethod m : ((PsiClass)myLookupItem.getObject()).getMethods()) {
            if (!(m instanceof PsiAnnotationMethod)) continue;
            final PsiAnnotationMemberValue defaultValue = ((PsiAnnotationMethod)m).getDefaultValue();
            if (defaultValue == null) return true;
          }
        }
      }
    }
    return false;
  }

  private boolean insertingAnnotation() {
    final Object obj = myLookupItem.getObject();
    if (!(obj instanceof PsiClass) || !((PsiClass)obj).isAnnotationType()) return false;

    final Document document = myEditor.getDocument();
    PsiDocumentManager.getInstance(myFile.getProject()).commitDocument(document);
    final int offset = myContext.getStartOffset();

    if (PsiTreeUtil.findElementOfClassAtOffset(myFile, offset, PsiImportStatement.class, false) != null) return false;

    //outside of any class: we are surely inserting an annotation
    if (PsiTreeUtil.findElementOfClassAtOffset(myFile, offset, PsiClass.class, false) == null) return true;

    //the easiest check that there's a @ before the identifier
    return PsiTreeUtil.findElementOfClassAtOffset(myFile, offset, PsiAnnotation.class, false) != null;

  }

  protected void removeEndOfIdentifier(boolean needParenth){
    JavaCompletionUtil.initOffsets(myContext.getFile(), myContext.getProject(), myContext.getOffsetMap());
    myDocument.deleteString(myContext.getSelectionEndOffset(), myContext.getOffsetMap().getOffset(CompletionInitializationContext.IDENTIFIER_END_OFFSET));
    if(myContext.getOffsetMap().getOffset(JavaCompletionUtil.LPAREN_OFFSET) > 0 && !needParenth){
      myDocument.deleteString(myContext.getOffsetMap().getOffset(JavaCompletionUtil.LPAREN_OFFSET),
                              myContext.getOffsetMap().getOffset(JavaCompletionUtil.ARG_LIST_END_OFFSET));
      JavaCompletionUtil.resetParensInfo(myContext.getOffsetMap());
    }
  }

  protected TailType getTailType(final char completionChar){
    switch(completionChar){
      case '.': return TailType.DOT;
      case ',': return TailType.COMMA;
      case ';': return TailType.SEMICOLON;
      case '=': return TailType.EQ;
      case ' ': return TailType.SPACE;
      case ':': return TailType.CASE_COLON; //?
      case '(': return TailTypeEx.SMART_LPARENTH;
      case '<':
      case '>':
      case '#':
      case '\"':
      case '[': return TailType.createSimpleTailType(completionChar);
      //case '!': if (!(myLookupItem.getObject() instanceof PsiVariable)) return TailType.EXCLAMATION;
    }
    final TailType attr = myLookupItem.getTailType();
    return attr == TailType.UNKNOWN ? TailType.NONE : attr;
  }

  private int processTail(TailType tailType, int caretOffset, int tailOffset) {
    myEditor.getCaretModel().moveToOffset(caretOffset);
    tailType.processTail(myEditor, tailOffset);
    return myEditor.getCaretModel().getOffset();
  }

  @Override
  protected void populateInsertMap(@NotNull final PsiFile file, @NotNull final OffsetMap offsetMap) {
    JavaCompletionUtil.initOffsets(file, file.getProject(), offsetMap);
  }

  public static void addImportForItem(PsiFile file, int startOffset, LookupElement item) throws IncorrectOperationException {
    PsiDocumentManager.getInstance(file.getProject()).commitAllDocuments();

    Object o = item.getObject();
    if (o instanceof PsiClass){
      PsiClass aClass = (PsiClass)o;
      if (aClass.getQualifiedName() == null) return;
      final String lookupString = item.getLookupString();
      int length = lookupString.length();
      final int i = lookupString.indexOf('<');
      if (i >= 0) length = i;
      final int newOffset = addImportForClass(file, startOffset, startOffset + length, aClass);
      shortenReference(file, newOffset);
    }
    else if (o instanceof PsiType){
      PsiType type = ((PsiType)o).getDeepComponentType();
      if (type instanceof PsiClassType) {
        PsiClass refClass = ((PsiClassType) type).resolve();
        if (refClass != null){
          int length = refClass.getName().length();
          addImportForClass(file, startOffset, startOffset + length, refClass);
        }
      }
    }
    else if (o instanceof PsiMethod){
      PsiMethod method = (PsiMethod)o;
      if (method.isConstructor()){
        PsiClass aClass = method.getContainingClass();
        if (aClass != null){
          int length = method.getName().length();
          addImportForClass(file, startOffset, startOffset + length, aClass);
        }
      }
    }
  }

  //need to shorten references in type argument list
  private static void shortenReference(final PsiFile file, final int offset) throws IncorrectOperationException {
    final PsiDocumentManager manager = PsiDocumentManager.getInstance(file.getProject());
    final Document document = manager.getDocument(file);
    manager.commitDocument(document);
    final PsiReference ref = file.findReferenceAt(offset);
    if (ref instanceof PsiJavaCodeReferenceElement) {
      JavaCodeStyleManager.getInstance(file.getProject()).shortenClassReferences((PsiJavaCodeReferenceElement)ref);
    }
  }

  private static int addImportForClass(PsiFile file, int startOffset, int endOffset, PsiClass aClass) throws IncorrectOperationException {
    if (!aClass.isValid()) {
      return startOffset;
    }

    SmartPsiElementPointer<PsiClass> pointer = SmartPointerManager.getInstance(file.getProject()).createSmartPsiElementPointer(aClass);
    LOG.assertTrue(CommandProcessor.getInstance().getCurrentCommand() != null);
    LOG.assertTrue(ApplicationManager.getApplication().isUnitTestMode() || ApplicationManager.getApplication().getCurrentWriteAction(null) != null);

    final PsiManager manager = file.getManager();

    final Document document = FileDocumentManager.getInstance().getDocument(file.getViewProvider().getVirtualFile());

    final PsiReference reference = file.findReferenceAt(startOffset);
    if (reference != null) {
      final PsiElement resolved = reference.resolve();
      if (resolved instanceof PsiClass) {
        if (((PsiClass)resolved).getQualifiedName() == null || manager.areElementsEquivalent(aClass, resolved)) {
          return startOffset;
        }
      }
    }

    String name = aClass.getName();
    document.replaceString(startOffset, endOffset, name);
    //PsiDocumentManager.getInstance(manager.getProject()).commitAllDocuments();

    final RangeMarker toDelete = insertSpace(endOffset, document);

    PsiDocumentManager.getInstance(manager.getProject()).commitAllDocuments();

    int newStartOffset = startOffset;
    PsiElement element = file.findElementAt(startOffset);
    if (element instanceof PsiIdentifier) {
      PsiElement parent = element.getParent();
      if (parent instanceof PsiJavaCodeReferenceElement && !((PsiJavaCodeReferenceElement)parent).isQualified() && !(parent.getParent() instanceof PsiPackageStatement)) {
        PsiJavaCodeReferenceElement ref = (PsiJavaCodeReferenceElement)parent;

        if (!aClass.getManager().areElementsEquivalent(aClass, resolveReference(ref))) {
          final PsiElement pointerElement = pointer.getElement();
          if (pointerElement instanceof PsiClass) {
            PsiElement newElement;
            if (!(ref instanceof PsiImportStaticReferenceElement)) {
              newElement = ref.bindToElement(pointerElement);
            }
            else {
              newElement = ((PsiImportStaticReferenceElement)ref).bindToTargetClass((PsiClass)pointerElement);
            }
            RangeMarker marker = document.createRangeMarker(newElement.getTextRange());
            CodeInsightUtilBase.forcePsiPostprocessAndRestoreElement(newElement);
            newStartOffset = marker.getStartOffset();
          }
        }
      }
    }

    if (toDelete.isValid()) {
      document.deleteString(toDelete.getStartOffset(), toDelete.getEndOffset());
    }

    return newStartOffset;
  }

  public static RangeMarker insertSpace(final int endOffset, final Document document) {
    final CharSequence chars = document.getCharsSequence();
    final int length = chars.length();
    final RangeMarker toDelete;
    if (endOffset < length && Character.isJavaIdentifierPart(chars.charAt(endOffset))){
      document.insertString(endOffset, " ");
      toDelete = document.createRangeMarker(endOffset, endOffset + 1);
    } else if (endOffset >= length) {
      toDelete = document.createRangeMarker(length, length);
    }
    else {
      toDelete = document.createRangeMarker(endOffset, endOffset);
    }
    toDelete.setGreedyToLeft(true);
    toDelete.setGreedyToRight(true);
    return toDelete;
  }

  static PsiElement resolveReference(final PsiReference psiReference) {
    if (psiReference instanceof PsiPolyVariantReference) {
      final ResolveResult[] results = ((PsiPolyVariantReference)psiReference).multiResolve(true);
      if (results.length == 1) return results[0].getElement();
    }
    return psiReference.resolve();
  }

  public static class InsertHandlerState{
    int tailOffset;
    int caretOffset;

    public InsertHandlerState(int caretOffset, int tailOffset){
      this.caretOffset = caretOffset;
      this.tailOffset = tailOffset;
    }
  }
}
