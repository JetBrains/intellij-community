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

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.TailTypes;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
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
import org.jetbrains.annotations.Nullable;

public class DefaultInsertHandler extends TemplateInsertHandler implements Cloneable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.completion.DefaultInsertHandler");

  public static final DefaultInsertHandler NO_TAIL_HANDLER = new DefaultInsertHandler(){
    @Override
    protected TailType getTailType(char completionChar, LookupItem item) {
      return TailType.NONE;
    }
  };

  public void handleInsert(final InsertionContext context, LookupElement item) {
    super.handleInsert(context, item);

    handleInsertInner(context, (LookupItem)item, context.getCompletionChar());
  }

  private void handleInsertInner(InsertionContext context, LookupItem item, final char completionChar) {
    LOG.assertTrue(CommandProcessor.getInstance().getCurrentCommand() != null);
    final Project project = context.getProject();
    final Editor editor = context.getEditor();
    final Document document = editor.getDocument();
    PsiDocumentManager.getInstance(project).commitDocument(document);

    final PsiFile file = context.getFile();

    TailType tailType = getTailType(completionChar, item);

    InsertHandlerState state = new InsertHandlerState(context.getSelectionEndOffset(), context.getSelectionEndOffset());

    final boolean needLeftParenth = isToInsertParenth(context, item);
    final boolean hasParams = needLeftParenth && hasParams(context, item);

    if (CompletionUtil.isOverwrite(item, completionChar)) {
      removeEndOfIdentifier(needLeftParenth && hasParams, context);
    }
    else if(context.getOffsetMap().getOffset(CompletionInitializationContext.IDENTIFIER_END_OFFSET) != context.getSelectionEndOffset()) {
      JavaCompletionUtil.resetParensInfo(context.getOffsetMap());
    }

    handleParentheses(hasParams, needLeftParenth, tailType, context, state);
    handleBrackets(item, document, state);

    if (item.getObject() instanceof PsiVariable) {
      if (completionChar == '!' && PsiType.BOOLEAN.isAssignableFrom(((PsiVariable) item.getObject()).getType())) {
        PsiDocumentManager.getInstance(project).commitDocument(document);
        final PsiReferenceExpression ref =
            PsiTreeUtil.findElementOfClassAtOffset(file, state.tailOffset - 1, PsiReferenceExpression.class, false);
        if (ref != null) {
          FeatureUsageTracker.getInstance().triggerFeatureUsed(CodeCompletionFeatures.EXCLAMATION_FINISH);
          document.insertString(ref.getTextRange().getStartOffset(), "!");
          state.caretOffset++;
          state.tailOffset++;
        }
      }
    }

    context.setTailOffset(state.tailOffset);
    state.caretOffset = processTail(tailType, state.caretOffset, state.tailOffset, editor);
    editor.getSelectionModel().removeSelection();

    qualifyIfNeeded(context, item);


    if (needLeftParenth && hasParams){
      // Invoke parameters popup
      AutoPopupController.getInstance(project).autoPopupParameterInfo(editor, null);
    }

    if (tailType == TailType.DOT){
      AutoPopupController.getInstance(project).autoPopupMemberLookup(editor, null);
    }

    if (completionChar == '#') {
      context.setLaterRunnable(new Runnable() {
        public void run() {
           new CodeCompletionHandlerBase(CompletionType.BASIC).invoke(project, editor, file);
        }
      });
    }

    if (insertingAnnotation(context, item)) {
      // Check if someone inserts annotation class that require @
      PsiElement elementAt = file.findElementAt(context.getStartOffset());
      final PsiElement parentElement = elementAt != null ? elementAt.getParent():null;

      if (elementAt instanceof PsiIdentifier &&
          (PsiTreeUtil.getParentOfType(elementAt, PsiAnnotationParameterList.class) != null ||
           parentElement instanceof PsiErrorElement && parentElement.getParent() instanceof PsiJavaFile // top level annotation without @
          )
          && isAtTokenNeeded(context)) {
        int expectedOffsetForAtToken = elementAt.getTextRange().getStartOffset();
        document.insertString(expectedOffsetForAtToken, "@");
      }
    }
  }

  private static void qualifyIfNeeded(InsertionContext context, LookupElement item) {
    try{
      final PsiFile file = context.getFile();
      if (item.getObject() instanceof PsiField) {
        PsiDocumentManager.getInstance(file.getProject()).commitAllDocuments();
        PsiReference reference = file.findReferenceAt(context.getStartOffset());
        if (reference instanceof PsiReferenceExpression && !((PsiReferenceExpression) reference).isQualified()) {
          final PsiField member = (PsiField)item.getObject();
          final PsiVariable target =
              JavaPsiFacade.getInstance(context.getProject()).getResolveHelper().resolveReferencedVariable(member.getName(), (PsiElement)reference);
          if (member.getManager().areElementsEquivalent(target, JavaCompletionUtil.getOriginalElement(member))) return;
          
          final PsiClass psiClass = member.getContainingClass();
          if (psiClass != null && StringUtil.isNotEmpty(psiClass.getName())) {
            context.getEditor().getDocument().insertString(context.getStartOffset(), psiClass.getName() + ".");
          }
        }
      }
      addImportForItem(file, context.getStartOffset(), item);
      if (context.getTailOffset() < 0) {  //hack, hack, hack. ideally the tail offset just should survive after the importing stuff
        context.setTailOffset(context.getEditor().getCaretModel().getOffset());
      }
    }
    catch(IncorrectOperationException e){
      LOG.error(e);
    }                                                                       
  }

  private static boolean isAtTokenNeeded(InsertionContext myContext) {
    HighlighterIterator iterator = ((EditorEx)myContext.getEditor()).getHighlighter().createIterator(myContext.getStartOffset());
    LOG.assertTrue(iterator.getTokenType() == JavaTokenType.IDENTIFIER);
    iterator.retreat();
    if (iterator.getTokenType() == TokenType.WHITE_SPACE) iterator.retreat();
    return iterator.getTokenType() != JavaTokenType.AT && iterator.getTokenType() != JavaTokenType.DOT;
  }

  private static void handleBrackets(LookupElement item, Document document, InsertHandlerState myState){
    // brackets
    final Integer bracketsAttr = (Integer)item.getUserData(LookupItem.BRACKETS_COUNT_ATTR);
    if (bracketsAttr != null){
      int count = bracketsAttr.intValue();
      if(count > 0)
        myState.caretOffset = myState.tailOffset + 1;
      for(int i = 0; i < count; i++){
        document.insertString(myState.tailOffset, "[]");
        myState.tailOffset += 2;
      }
    }
  }

  private static void handleParentheses(final boolean hasParams, final boolean needParenth, TailType tailType, InsertionContext context, InsertHandlerState myState){
    final Document document = context.getEditor().getDocument();
    boolean insertRightParenth = tailType != TailType.SMART_COMPLETION;

    if (needParenth){
      if (context.getOffsetMap().getOffset(JavaCompletionUtil.LPAREN_OFFSET) >= 0 && context.getOffsetMap().getOffset(JavaCompletionUtil.ARG_LIST_END_OFFSET) >= 0){
        myState.tailOffset = context.getOffsetMap().getOffset(JavaCompletionUtil.ARG_LIST_END_OFFSET);
        if (context.getOffsetMap().getOffset(JavaCompletionUtil.RPAREN_OFFSET) < 0 && insertRightParenth){
          document.insertString(myState.tailOffset, ")");
          myState.tailOffset += 1;
        }
        if (hasParams){
          myState.caretOffset = context.getOffsetMap().getOffset(JavaCompletionUtil.LPAREN_OFFSET) + 1;
        }
        else{
          myState.caretOffset = context.getOffsetMap().getOffset(JavaCompletionUtil.ARG_LIST_END_OFFSET);
        }
      }
      else{
        final CodeStyleSettings styleSettings = CodeStyleSettingsManager.getSettings(context.getProject());
        myState.tailOffset = context.getSelectionEndOffset();
        myState.caretOffset = context.getSelectionEndOffset();

        if(styleSettings.SPACE_BEFORE_METHOD_CALL_PARENTHESES){
          document.insertString(myState.tailOffset++, " ");
          myState.caretOffset ++;
        }
        if (insertRightParenth) {
          final CharSequence charsSequence = document.getCharsSequence();
          if (charsSequence.length() <= myState.tailOffset || charsSequence.charAt(myState.tailOffset) != '(') {
            document.insertString(myState.tailOffset, "(");
          }

          document.insertString(myState.tailOffset + 1, ")");
          if (hasParams){
            myState.tailOffset += 2;
            myState.caretOffset++;
          }
          else{
            if (tailType != TailTypes.CALL_RPARENTH) {
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
          document.insertString(myState.tailOffset++, "(");
          myState.caretOffset ++;
        }

        if(hasParams && styleSettings.SPACE_WITHIN_METHOD_CALL_PARENTHESES){
          document.insertString(myState.caretOffset++, " ");
          myState.tailOffset++;
        }
      }
    }
  }

  protected static boolean isToInsertParenth(InsertionContext context, LookupElement item){
    return insertingAnnotationWithParameters(context, item);
  }

  private static boolean hasParams(InsertionContext context, LookupElement item){
    final String lookupString = item.getLookupString();
    if (PsiKeyword.SYNCHRONIZED.equals(lookupString)) {
      final PsiElement place = context.getFile().findElementAt(context.getStartOffset());
      return PsiTreeUtil.getParentOfType(place, PsiMember.class, PsiCodeBlock.class) instanceof PsiCodeBlock;
    }
    else if(PsiKeyword.CATCH.equals(lookupString) ||
            PsiKeyword.SWITCH.equals(lookupString) ||
            PsiKeyword.WHILE.equals(lookupString) ||
            PsiKeyword.FOR.equals(lookupString))
      return true;
    else if (insertingAnnotationWithParameters(context, item)) {
      return true;
    }
    return false;
  }

  private static boolean insertingAnnotationWithParameters(InsertionContext context, LookupElement item) {
    if(insertingAnnotation(context, item)) {
      final Document document = context.getEditor().getDocument();
      PsiDocumentManager.getInstance(context.getProject()).commitDocument(document);
      PsiElement elementAt = context.getFile().findElementAt(context.getStartOffset());
      if (elementAt instanceof PsiIdentifier) {
        final PsiModifierListOwner parent = PsiTreeUtil.getParentOfType(elementAt, PsiModifierListOwner.class, false, PsiCodeBlock.class);
        if (parent != null) {
          for (PsiMethod m : ((PsiClass)item.getObject()).getMethods()) {
            if (!(m instanceof PsiAnnotationMethod)) continue;
            final PsiAnnotationMemberValue defaultValue = ((PsiAnnotationMethod)m).getDefaultValue();
            if (defaultValue == null) return true;
          }
        }
      }
    }
    return false;
  }

  private static boolean insertingAnnotation(InsertionContext context, LookupElement item) {
    final Object obj = item.getObject();
    if (!(obj instanceof PsiClass) || !((PsiClass)obj).isAnnotationType()) return false;

    final Document document = context.getEditor().getDocument();
    PsiDocumentManager.getInstance(context.getProject()).commitDocument(document);
    final int offset = context.getStartOffset();

    final PsiFile file = context.getFile();

    if (PsiTreeUtil.findElementOfClassAtOffset(file, offset, PsiImportStatement.class, false) != null) return false;

    //outside of any class: we are surely inserting an annotation
    if (PsiTreeUtil.findElementOfClassAtOffset(file, offset, PsiClass.class, false) == null) return true;

    //the easiest check that there's a @ before the identifier
    return PsiTreeUtil.findElementOfClassAtOffset(file, offset, PsiAnnotation.class, false) != null;

  }

  protected static void removeEndOfIdentifier(boolean needParenth, InsertionContext context){
    final Document document = context.getEditor().getDocument();
    JavaCompletionUtil.initOffsets(context.getFile(), context.getProject(), context.getOffsetMap());
    document.deleteString(context.getSelectionEndOffset(), context.getOffsetMap().getOffset(CompletionInitializationContext.IDENTIFIER_END_OFFSET));
    if(context.getOffsetMap().getOffset(JavaCompletionUtil.LPAREN_OFFSET) > 0 && !needParenth){
      document.deleteString(context.getOffsetMap().getOffset(JavaCompletionUtil.LPAREN_OFFSET),
                              context.getOffsetMap().getOffset(JavaCompletionUtil.ARG_LIST_END_OFFSET));
      JavaCompletionUtil.resetParensInfo(context.getOffsetMap());
    }
  }

  protected TailType getTailType(final char completionChar, LookupItem item){
    switch(completionChar){
      case '.': return TailType.DOT;
      case ',': return TailType.COMMA;
      case ';': return TailType.SEMICOLON;
      case '=': return TailType.EQ;
      case ' ': return TailType.SPACE;
      case ':': return TailType.CASE_COLON; //?
      case '<':
      case '>':
      case '#':
      case '\"':
      case '[': return TailType.createSimpleTailType(completionChar);
    }
    final TailType attr = item.getTailType();
    return attr == TailType.UNKNOWN ? TailType.NONE : attr;
  }

  private static int processTail(TailType tailType, int caretOffset, int tailOffset, Editor editor) {
    editor.getCaretModel().moveToOffset(caretOffset);
    tailType.processTail(editor, tailOffset);
    return editor.getCaretModel().getOffset();
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
    assert document != null;
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

  @Nullable
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
