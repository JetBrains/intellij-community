package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.*;
import com.intellij.codeInsight.completion.simple.SimpleInsertHandlerFactory;
import com.intellij.codeInsight.completion.simple.SimpleLookupItem;
import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.codeInsight.generation.PsiGenerationInfo;
import com.intellij.codeInsight.generation.PsiMethodMember;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateEditingAdapter;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.ide.util.MemberChooser;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.HighlighterIterator;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

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
    if (!(item instanceof SimpleLookupItem)) {
      if (item.getObject() instanceof PsiMethod) {
        PsiMethod method = (PsiMethod)item.getObject();
        LookupItem simpleItem = new SimpleLookupItem(method, item.getLookupString()).setInsertHandler(SimpleInsertHandlerFactory.createMethodInsertHandler(method));
        simpleItem.setAttribute(LookupItem.FORCE_SHOW_SIGNATURE_ATTR, item.getAttribute(LookupItem.FORCE_SHOW_SIGNATURE_ATTR));
        simpleItem.setAttribute(CompletionUtil.TAIL_TYPE_ATTR, item.getAttribute(CompletionUtil.TAIL_TYPE_ATTR));
        ((InsertHandler) simpleItem.getAttribute(LookupItem.INSERT_HANDLER_ATTR)).handleInsert(context, startOffset, data, simpleItem, signatureSelected, completionChar);
        return;
      }
    }

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

  private boolean handleInsertInner(CompletionContext context,
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
      handleTemplate(startOffset, signatureSelected, completionChar);
      // we could not clear in this case since handleTemplate has templateFinished lisntener that works
      // with e.g. myLookupItem
      return false;
    }

    TailType tailType = getTailType(completionChar);

    //adjustContextAfterLookupStringInsertion();
    myState = new InsertHandlerState(myContext.selectionEndOffset, myContext.selectionEndOffset);

    final boolean needLeftParenth = isToInsertParenth(tailType);
    final boolean hasParams = needLeftParenth && hasParams();

    if (CompletionUtil.isOverwrite(item, completionChar))
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
    myEditor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    myEditor.getSelectionModel().removeSelection();

    try{
      final int previousStartOffset = myStartOffset;
      myStartOffset = addImportForItem(myFile, previousStartOffset, myLookupItem);
      myContext.startOffset += (myStartOffset - previousStartOffset);
    }
    catch(IncorrectOperationException e){
      LOG.error(e);
    }

    if (needLeftParenth && hasParams){
      // Invoke parameters popup
      AutoPopupController.getInstance(myProject).autoPopupParameterInfo(myEditor, null);
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
      // Check if someone inserts annotation class that require @
      final Document document = context.editor.getDocument();
      PsiDocumentManager.getInstance(context.project).commitDocument(document);
      PsiElement elementAt = myFile.findElementAt(myContext.startOffset);
      final PsiElement parentElement = elementAt != null ? elementAt.getParent():null;

      if (elementAt instanceof PsiIdentifier &&
          ( PsiTreeUtil.getParentOfType(elementAt, PsiAnnotationParameterList.class) != null || //we are inserting '@' only in annotation parameters
            (parentElement instanceof PsiErrorElement && parentElement.getParent() instanceof PsiJavaFile) // top level annotation without @
          )
          && isAtTokenNeeded()) {
        PsiElement parent = PsiTreeUtil.getParentOfType(elementAt, PsiModifierListOwner.class, PsiCodeBlock.class);
        if (parent == null && parentElement instanceof PsiErrorElement) {
          PsiElement nextElement = parentElement.getNextSibling();
          if (nextElement instanceof PsiWhiteSpace) nextElement = nextElement.getNextSibling();
          if (nextElement instanceof PsiClass) parent = nextElement;
        }

        if (parent instanceof PsiModifierListOwner) {
          int expectedOffsetForAtToken = elementAt.getTextRange().getStartOffset();
          document.insertString(expectedOffsetForAtToken, "@");
        }
      }
    }
    return true;
  }

  private boolean isAtTokenNeeded() {
    HighlighterIterator iterator = ((EditorEx)myContext.editor).getHighlighter().createIterator(myContext.startOffset);
    LOG.assertTrue(iterator.getTokenType() == JavaTokenType.IDENTIFIER);
    iterator.retreat();
    if (iterator.getTokenType() == JavaTokenType.WHITE_SPACE) iterator.retreat();
    return iterator.getTokenType() != JavaTokenType.AT && iterator.getTokenType() != JavaTokenType.DOT;
  }

  private static boolean isTemplateToBeCompleted(final LookupItem lookupItem) {
    return lookupItem.getObject() instanceof Template
           && lookupItem.getAttribute(EXPANDED_TEMPLATE_ATTR) == null;
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

  private void handleParenses(final boolean hasParams, final boolean needParenth, TailType tailType){
    final CodeInsightSettings settings = CodeInsightSettings.getInstance();
    final boolean generateAnonymousBody = myLookupItem.getAttribute(LookupItem.GENERATE_ANONYMOUS_BODY_ATTR) != null;
    boolean insertRightParenth = !settings.INSERT_SINGLE_PARENTH
                                 || (settings.INSERT_DOUBLE_PARENTH_WHEN_NO_ARGS && !hasParams)
                                 || generateAnonymousBody
                                 || (tailType != TailType.NONE && tailType != TailType.LPARENTH);

//    if(tailType == SimpleTailType.LPARENTH) tailType = SimpleTailType.NONE; //???
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

  private boolean isToInsertParenth(TailType tailType){
    boolean needParens = false;
    if (tailType == TailType.LPARENTH){
      needParens = true;
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

  private boolean hasParams(){
    boolean hasParms = false;
    if (myLookupItem.getAttribute(LookupItem.NEW_OBJECT_ATTR) != null){
      PsiDocumentManager.getInstance(myProject).commitDocument(myDocument);
      final PsiClass aClass = (PsiClass)myLookupItem.getObject();

      final PsiElement place = myFile.findElementAt(myStartOffset);

      final PsiMethod[] constructors = aClass.getConstructors();
      for (PsiMethod constructor : constructors) {
        if (!aClass.getManager().getResolveHelper().isAccessible(constructor, place, null)) continue;
        if (constructor.getParameterList().getParametersCount() > 0) {
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
      PsiElement elementAt = myFile.findElementAt(myContext.startOffset);
      if (elementAt instanceof PsiIdentifier) {
        if (insertingNotRuntimeAnnotation() || PsiTreeUtil.getParentOfType(elementAt, PsiAnnotationParameterList.class) != null) {
          final PsiElement parent = PsiTreeUtil.getParentOfType(elementAt, PsiModifierListOwner.class, PsiCodeBlock.class);
          if (parent instanceof PsiModifierListOwner) {
            final PsiClass psiClass = (PsiClass)myLookupItem.getObject();
            for (PsiMethod m : psiClass.getMethods()) {
              if (!(m instanceof PsiAnnotationMethod)) continue;
              final PsiAnnotationMemberValue defaultValue = ((PsiAnnotationMethod)m).getDefaultValue();
              if (defaultValue == null) return true;
            }
          }
        }
      }
    }
    return false;
  }

  private boolean insertingNotRuntimeAnnotation() {
    final Object obj = myLookupItem.getObject();
    if (!(obj instanceof PsiClass)) return false;
    final PsiClass aClass = ((PsiClass)obj);
    if (!aClass.isAnnotationType()) return false;
    final PsiAnnotation retentionPolicy = AnnotationUtil.findAnnotation((PsiClass)obj, "java.lang.annotation.Retention");
    if (retentionPolicy == null) return true; //CLASS by default
    final PsiAnnotationMemberValue value = retentionPolicy.findAttributeValue(PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME);
    return !(value instanceof PsiReferenceExpression) || !"RUNTIME".equals(((PsiReferenceExpression)value).getReferenceName());
  }

  private boolean insertingAnnotation() {
    final Object obj = myLookupItem.getObject();
    return obj instanceof PsiClass && ((PsiClass)obj).isAnnotationType();
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

  private TailType getTailType(final char completionChar){
    return getTailType(completionChar, myLookupItem);
  }

  @NotNull
  public static TailType getTailType(final char completionChar, final LookupItem item) {
    switch(completionChar){
      case '.': return TailType.DOT;
      case ',': return TailType.COMMA;
      case ';': return TailType.SEMICOLON;
      case '=': return TailType.EQ;
      case ' ': return TailType.SPACE;
      case ':': return TailType.CASE_COLON; //?
      case '(': return TailType.LPARENTH;
      case '<': case '>': case '[': return TailType.createSimpleTailType(completionChar);
    }
    final TailType attr = (TailType)item.getAttribute(CompletionUtil.TAIL_TYPE_ATTR);
    return attr != null ? attr : TailType.NONE;
  }

  private void handleTemplate(final int templateStartOffset, final boolean signatureSelected, final char completionChar){
    Template template = (Template)myLookupItem.getObject();

    final RangeMarker offsetRangeMarker = myContext.editor.getDocument().createRangeMarker(templateStartOffset, templateStartOffset);

    TemplateManager.getInstance(myProject).startTemplate(
      myContext.editor,
      template,
      new TemplateEditingAdapter() {
        public void templateFinished(Template template) {
          myLookupItem.setAttribute(EXPANDED_TEMPLATE_ATTR, Boolean.TRUE);

          if (!offsetRangeMarker.isValid()) return;

          final Editor editor = myContext.editor;
          final int startOffset = offsetRangeMarker.getStartOffset();
          final int endOffset = editor.getCaretModel().getOffset();
          String lookupString = editor.getDocument().getCharsSequence().subSequence(startOffset, endOffset).toString();
          myLookupItem.setLookupString(lookupString);

          CompletionContext newContext = new CompletionContext(myContext.project, editor, myContext.file, startOffset, endOffset);
          handleInsert(newContext, myStartOffset, myLookupData, myLookupItem, signatureSelected, completionChar);
        }
      }
    );
  }

  private int processTail(TailType tailType, int caretOffset, int tailOffset) {
    myEditor.getCaretModel().moveToOffset(caretOffset);
    tailType.processTail(myEditor, tailOffset);
    return myEditor.getCaretModel().getOffset();
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
                    PsiGenerationInfo[] prototypes = OverrideImplementUtil.convert2GenerationInfos(OverrideImplementUtil.overrideOrImplementMethods(aClass, candidatesToImplement, false, false));
                    PsiGenerationInfo[] resultMembers = GenerateMembersUtil.insertMembersBeforeAnchor(aClass, null, prototypes);
                    GenerateMembersUtil.positionCaret(myEditor, resultMembers[0].getPsiMember(), true);
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

    List<PsiMethodMember> methods = new ArrayList<PsiMethodMember>();
    for (final PsiMethod method : allBaseMethods) {
      if (OverrideImplementUtil.isOverridable(method)) {
        methods.add(new PsiMethodMember(method, PsiSubstitutor.UNKNOWN));
      }
    }

    boolean isJdk15Enabled = LanguageLevel.JDK_1_5.compareTo(PsiUtil.getLanguageLevel(aClass)) <= 0;
    final PsiMethodMember[] array = methods.toArray(new PsiMethodMember[methods.size()]);
    final MemberChooser<PsiMethodMember> chooser = new MemberChooser<PsiMethodMember>(array, false, true, project, isJdk15Enabled);
    chooser.setTitle(CompletionBundle.message("completion.smarttype.select.methods.to.override"));
    chooser.setCopyJavadocVisible(true);

    chooser.show();
    List<PsiMethodMember> selected = chooser.getSelectedElements();
    if (selected == null || selected.size() == 0) return;


    try{
      PsiMethodMember[] selectedArray = selected.toArray(new PsiMethodMember[selected.size()]);
      final PsiGenerationInfo<PsiMethod>[] prototypes = OverrideImplementUtil.overrideOrImplementMethods(aClass, selectedArray, chooser.isCopyJavadoc(), chooser.isInsertOverrideAnnotation());

      final int offset = editor.getCaretModel().getOffset();

      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          try{
            for (PsiGenerationInfo<PsiMethod> prototype : prototypes) {
              PsiStatement[] statements = prototype.getPsiMember().getBody().getStatements();
              if (statements.length > 0 && prototype.getPsiMember().getReturnType() == PsiType.VOID) {
                statements[0].delete(); // remove "super(..)" call
              }
            }

            PsiGenerationInfo[] resultMembers = GenerateMembersUtil.insertMembersAtOffset(aClass.getContainingFile(), offset, prototypes);
            GenerateMembersUtil.positionCaret(editor, resultMembers[0].getPsiMember(), true);
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
      final String lookupString = item.getLookupString();
      int length = lookupString.length();
      final int i = lookupString.indexOf('<');
      if (i >= 0) length = i;
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

  //need to shorten references in type argument list
  private static void shortenReference(final PsiFile file, final int offset) throws IncorrectOperationException {
    final PsiDocumentManager manager = PsiDocumentManager.getInstance(file.getProject());
    final Document document = manager.getDocument(file);
    manager.commitDocument(document);
    final PsiReference ref = file.findReferenceAt(offset);
    if (ref instanceof PsiJavaCodeReferenceElement) {
      file.getManager().getCodeStyleManager().shortenClassReferences(((PsiJavaCodeReferenceElement)ref));
    }
  }

  private static int addImportForClass(PsiFile file, int startOffset, int endOffset, PsiClass aClass) throws IncorrectOperationException {
    SmartPsiElementPointer pointer = SmartPointerManager.getInstance(file.getProject()).createSmartPsiElementPointer(aClass);
    LOG.assertTrue(CommandProcessor.getInstance().getCurrentCommand() != null);
    LOG.assertTrue(ApplicationManager.getApplication().isUnitTestMode() || ApplicationManager.getApplication().getCurrentWriteAction(null) != null);

    final PsiManager manager = file.getManager();
    final PsiResolveHelper helper = manager.getResolveHelper();

    final Document document = FileDocumentManager.getInstance().getDocument(file.getViewProvider().getVirtualFile());

    CharSequence chars = document.getCharsSequence();
    int length = document.getTextLength();
    int newStartOffset = startOffset;

    final PsiReference reference = file.findReferenceAt(startOffset);
    if (reference != null) {
      final PsiElement resolved = reference.resolve();
      if (resolved instanceof PsiClass) {
        if ((((PsiClass)resolved).getQualifiedName() == null/* local classes and parameters*/
                                 || manager.areElementsEquivalent(aClass, resolved))) return newStartOffset;

      }
    }

    boolean insertSpace = endOffset < length && Character.isJavaIdentifierPart(chars.charAt(endOffset));

    if (insertSpace){
      document.insertString(endOffset, " ");
    }
    String name = aClass.getName();
    document.replaceString(startOffset, endOffset, name);
    endOffset = startOffset + name.length();

    PsiDocumentManager.getInstance(manager.getProject()).commitAllDocuments();

    PsiElement element = file.findElementAt(startOffset);
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

  public static class InsertHandlerState{
    int tailOffset;
    int caretOffset;

    public InsertHandlerState(int caretOffset, int tailOffset){
      this.caretOffset = caretOffset;
      this.tailOffset = tailOffset;
    }
  }
}
