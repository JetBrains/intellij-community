package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.completion.simple.PsiMethodInsertHandler;
import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.codeInsight.generation.PsiGenerationInfo;
import com.intellij.codeInsight.generation.PsiMethodMember;
import com.intellij.codeInsight.lookup.LookupElementDecorator;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.ide.util.MemberChooser;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
* @author peter
*/
class ConstructorInsertHandler implements InsertHandler<LookupElementDecorator<LookupItem>> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.completion.ConstructorInsertHandler");
  public static final ConstructorInsertHandler INSTANCE = new ConstructorInsertHandler();

  public void handleInsert(InsertionContext context, LookupElementDecorator<LookupItem> item) {
    @SuppressWarnings({"unchecked"}) final LookupItem<PsiClass> delegate = item.getDelegate();

    final PsiElement position = SmartCompletionDecorator.getPosition(context, delegate);
    final PsiExpression enclosing = PsiTreeUtil.getContextOfType(position, PsiExpression.class, true);
    final PsiAnonymousClass anonymousClass = PsiTreeUtil.getParentOfType(position, PsiAnonymousClass.class);
    final boolean inAnonymous = anonymousClass != null && anonymousClass.getParent() == enclosing;

    insertParentheses(context, delegate, delegate.getObject());

    DefaultInsertHandler.addImportForItem(context.getFile(), context.getStartOffset(), delegate);


    if (item.getUserData(LookupItem.BRACKETS_COUNT_ATTR) == null && !inAnonymous) {
      if (((PsiClass)item.getObject()).hasModifierProperty(PsiModifier.ABSTRACT)) {
        FeatureUsageTracker.getInstance().triggerFeatureUsed("editing.completion.smarttype.anonymous");

        PostprocessReformattingAspect.getInstance(context.getProject()).doPostponedFormatting(context.getFile().getViewProvider());

        final Editor editor = context.getEditor();
        final int offset = context.getTailOffset();
        editor.getDocument().insertString(offset, " {}");
        editor.getCaretModel().moveToOffset(offset + 2);
        context.setLaterRunnable(generateAnonymousBody(editor, context.getFile()));
      }
      else {
        FeatureUsageTracker.getInstance().triggerFeatureUsed("editing.completion.smarttype.afternew");
      }
    }
  }

  public static void insertParentheses(InsertionContext context, LookupItem delegate, final PsiClass psiClass) {
    final PsiElement place = context.getFile().findElementAt(context.getStartOffset());
    final PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(context.getProject()).getResolveHelper();
    assert place != null;
    boolean hasParams = false;
    for (PsiMethod constructor : psiClass.getConstructors()) {
      if (!resolveHelper.isAccessible(constructor, place, null)) continue;
      if (constructor.getParameterList().getParametersCount() > 0) {
        hasParams = true;
        break;
      }
    }

    PsiMethodInsertHandler.insertParentheses(context, delegate, false, hasParams);
  }

  private static Runnable generateAnonymousBody(final Editor editor, PsiFile file) {
    final Project project = file.getProject();
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    int offset = editor.getCaretModel().getOffset();
    PsiElement element = file.findElementAt(offset);
    if (element == null) return null;
    if (element.getParent() instanceof PsiAnonymousClass){
      try{
        CodeStyleManager.getInstance(project).reformat(element.getParent());
      }
      catch(IncorrectOperationException e){
        LOG.error(e);
      }
      offset = element.getParent().getTextRange().getEndOffset() - 1;
      editor.getCaretModel().moveToOffset(offset);
      editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
      editor.getSelectionModel().removeSelection();
    }
    final SmartPsiElementPointer<PsiElement> pointer = SmartPointerManager.getInstance(project).createSmartPsiElementPointer(element);
    return new Runnable() {
      public void run(){
        CommandProcessor.getInstance().executeCommand(project, new Runnable() {
          public void run() {
            PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
            PsiElement element = pointer.getElement();
            if (element == null) return;

            while(true){
              if (element instanceof PsiFile) return;
              PsiElement parent = element.getParent();
              if (parent instanceof PsiAnonymousClass) break;
              element = parent;
            }
            final PsiAnonymousClass aClass = (PsiAnonymousClass)element.getParent();

            final Collection<CandidateInfo> candidatesToImplement = OverrideImplementUtil.getMethodsToOverrideImplement(aClass, true);
            boolean invokeOverride = candidatesToImplement.isEmpty();
            if (invokeOverride){
              chooseAndOverrideMethodsInAdapter(project, editor, aClass);
            }
            else{
              ApplicationManager.getApplication().runWriteAction(new Runnable() {
                public void run() {
                  try{
                    List<PsiMethod> methods = OverrideImplementUtil.overrideOrImplementMethodCandidates(aClass, candidatesToImplement, false);
                    List<PsiGenerationInfo<PsiMethod>> prototypes = OverrideImplementUtil.convert2GenerationInfos(methods);
                    List<PsiGenerationInfo<PsiMethod>> resultMembers = GenerateMembersUtil.insertMembersBeforeAnchor(aClass, null, prototypes);
                    GenerateMembersUtil.positionCaret(editor, resultMembers.get(0).getPsiMember(), true);
                  }
                  catch(IncorrectOperationException ioe){
                    LOG.error(ioe);
                  }
                }
              });
            }

          }
        }, CompletionBundle.message("completion.smart.type.generate.anonymous.body"), null, UndoConfirmationPolicy.DEFAULT, editor.getDocument());
      }
    };
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

    boolean canInsertOverride = PsiUtil.isLanguageLevel5OrHigher(aClass) && (PsiUtil.isLanguageLevel6OrHigher(aClass) || !aClass.isInterface());
    final PsiMethodMember[] array = methods.toArray(new PsiMethodMember[methods.size()]);
    final MemberChooser<PsiMethodMember> chooser = new MemberChooser<PsiMethodMember>(array, false, true, project, canInsertOverride);
    chooser.setTitle(CompletionBundle.message("completion.smarttype.select.methods.to.override"));
    chooser.setCopyJavadocVisible(true);

    chooser.show();
    List<PsiMethodMember> selected = chooser.getSelectedElements();
    if (selected == null || selected.isEmpty()) return;


    try{
      final List<PsiGenerationInfo<PsiMethod>> prototypes = OverrideImplementUtil.overrideOrImplementMethods(aClass, selected, chooser.isCopyJavadoc(), chooser.isInsertOverrideAnnotation());

      final int offset = editor.getCaretModel().getOffset();

      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          try{
            for (PsiGenerationInfo<PsiMethod> prototype : prototypes) {
              PsiStatement[] statements = prototype.getPsiMember().getBody().getStatements();
              if (statements.length > 0 && PsiType.VOID.equals(prototype.getPsiMember().getReturnType())) {
                statements[0].delete(); // remove "super(..)" call
              }
            }

            List<PsiGenerationInfo<PsiMethod>> resultMembers = GenerateMembersUtil.insertMembersAtOffset(aClass.getContainingFile(), offset, prototypes);
            GenerateMembersUtil.positionCaret(editor, resultMembers.get(0).getPsiMember(), true);
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


}
