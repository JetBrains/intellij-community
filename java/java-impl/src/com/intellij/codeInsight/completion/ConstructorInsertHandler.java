package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.ExpectedTypesProvider;
import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.codeInsight.generation.PsiGenerationInfo;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElementDecorator;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.PsiTypeLookupItem;
import com.intellij.featureStatistics.FeatureUsageTracker;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/**
* @author peter
*/
public class ConstructorInsertHandler implements InsertHandler<LookupElementDecorator<LookupItem>> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.completion.ConstructorInsertHandler");
  public static final ConstructorInsertHandler SMART_INSTANCE = new ConstructorInsertHandler(true);
  public static final ConstructorInsertHandler BASIC_INSTANCE = new ConstructorInsertHandler(false);
  static final OffsetKey PARAM_LIST_START = OffsetKey.create("paramListStart");
  static final OffsetKey PARAM_LIST_END = OffsetKey.create("paramListEnd");
  private final boolean mySmart;

  private ConstructorInsertHandler(boolean smart) {
    mySmart = smart;
  }

  @Override
  public void handleInsert(InsertionContext context, LookupElementDecorator<LookupItem> item) {
    @SuppressWarnings({"unchecked"}) final LookupItem<PsiClass> delegate = item.getDelegate();

    PsiClass psiClass = (PsiClass)item.getObject();

    boolean isAbstract = psiClass.hasModifierProperty(PsiModifier.ABSTRACT);

    if (Lookup.REPLACE_SELECT_CHAR == context.getCompletionChar()) {
      final int plStart = context.getOffset(PARAM_LIST_START);
      final int plEnd = context.getOffset(PARAM_LIST_END);
      if (plStart >= 0 && plEnd >= 0) {
        context.getDocument().deleteString(plStart, plEnd);
      }
    }

    context.commitDocument();

    OffsetKey insideRef = context.trackOffset(context.getTailOffset(), false);

    final PsiElement position = SmartCompletionDecorator.getPosition(context, delegate);
    final PsiExpression enclosing = PsiTreeUtil.getContextOfType(position, PsiExpression.class, true);
    final PsiAnonymousClass anonymousClass = PsiTreeUtil.getParentOfType(position, PsiAnonymousClass.class);
    final boolean inAnonymous = anonymousClass != null && anonymousClass.getParent() == enclosing;
    boolean fillTypeArgs = false;
    if (delegate instanceof PsiTypeLookupItem) {
      fillTypeArgs = !isRawTypeExpected(context, (PsiTypeLookupItem)delegate) &&
                     psiClass.getTypeParameters().length > 0 &&
                     ((PsiTypeLookupItem)delegate).calcGenerics(position, context).isEmpty() &&
                     context.getCompletionChar() != '(';

      if (context.getDocument().getTextLength() > context.getTailOffset() &&
          context.getDocument().getCharsSequence().charAt(context.getTailOffset()) == '<') {
        PsiJavaCodeReferenceElement ref = PsiTreeUtil.findElementOfClassAtOffset(context.getFile(), context.getTailOffset(), PsiJavaCodeReferenceElement.class, false);
        if (ref != null) {
          PsiReferenceParameterList parameterList = ref.getParameterList();
          if (parameterList != null && context.getTailOffset() == parameterList.getTextRange().getStartOffset()) {
            context.getDocument().deleteString(parameterList.getTextRange().getStartOffset(), parameterList.getTextRange().getEndOffset());
            context.commitDocument();
          }
        }
      }

      delegate.handleInsert(context);
      PostprocessReformattingAspect.getInstance(context.getProject()).doPostponedFormatting(context.getFile().getViewProvider());
    }

    if (item.getDelegate() instanceof JavaPsiClassReferenceElement) {
      PsiTypeLookupItem.addImportForItem(context, psiClass);
    }


    insertParentheses(context, delegate, psiClass, !inAnonymous && isAbstract);

    if (inAnonymous) {
      return;
    }

    if (mySmart) {
      FeatureUsageTracker.getInstance().triggerFeatureUsed(JavaCompletionFeatures.AFTER_NEW);
    }
    if (isAbstract) {
      PostprocessReformattingAspect.getInstance(context.getProject()).doPostponedFormatting(context.getFile().getViewProvider());

      final Editor editor = context.getEditor();
      final int offset = context.getTailOffset();
      editor.getDocument().insertString(offset, " {}");
      editor.getCaretModel().moveToOffset(offset + 2);

      if (fillTypeArgs && JavaCompletionUtil.promptTypeArgs(context, context.getOffset(insideRef))) return;

      context.setLaterRunnable(generateAnonymousBody(editor, context.getFile()));
    }
    else {
      PsiDocumentManager.getInstance(context.getProject()).commitAllDocuments();
      final PsiNewExpression newExpression =
        PsiTreeUtil.findElementOfClassAtOffset(context.getFile(), context.getStartOffset(), PsiNewExpression.class, false);
      if (newExpression != null) {
        final PsiJavaCodeReferenceElement classReference = newExpression.getClassOrAnonymousClassReference();
        if (classReference != null) {
          CodeStyleManager.getInstance(context.getProject()).reformat(classReference);
        }
      }
      if (mySmart) {
        FeatureUsageTracker.getInstance().triggerFeatureUsed(JavaCompletionFeatures.AFTER_NEW);
      }
      if (fillTypeArgs && JavaCompletionUtil.promptTypeArgs(context, context.getOffset(insideRef))) return;
    }
  }

  static boolean isRawTypeExpected(InsertionContext context, PsiTypeLookupItem delegate) {
    PsiNewExpression newExpr =
      PsiTreeUtil.findElementOfClassAtOffset(context.getFile(), context.getStartOffset(), PsiNewExpression.class, false);
    if (newExpr != null) {
      for (ExpectedTypeInfo info : ExpectedTypesProvider.getExpectedTypes(newExpr, true)) {
        PsiType expected = info.getDefaultType();
        if (expected.isAssignableFrom(delegate.getPsiType())) {
          if (expected instanceof PsiClassType && ((PsiClassType)expected).isRaw()) {
            return true;
          }
        }
      }
    }
    return false;
  }

  public static boolean insertParentheses(InsertionContext context,
                                          LookupItem delegate,
                                          final PsiClass psiClass,
                                          final boolean forAnonymous) {
    if (context.getCompletionChar() == '[') {
      return false;
    }

    final PsiElement place = context.getFile().findElementAt(context.getStartOffset());
    assert place != null;
    boolean hasParams = hasConstructorParameters(psiClass, place);

    JavaCompletionUtil.insertParentheses(context, delegate, false, hasParams, forAnonymous);

    return true;
  }

  static boolean hasConstructorParameters(PsiClass psiClass, @NotNull PsiElement place) {
    final PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(place.getProject()).getResolveHelper();
    boolean hasParams = false;
    for (PsiMethod constructor : psiClass.getConstructors()) {
      if (!resolveHelper.isAccessible(constructor, place, null)) continue;
      if (constructor.getParameterList().getParametersCount() > 0) {
        hasParams = true;
        break;
      }
    }
    return hasParams;
  }

  @Nullable
  private static Runnable generateAnonymousBody(final Editor editor, final PsiFile file) {
    final Project project = file.getProject();
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    int offset = editor.getCaretModel().getOffset();
    PsiElement element = file.findElementAt(offset);
    if (element == null) return null;

    PsiElement parent = element.getParent();
    if (!(parent instanceof PsiAnonymousClass)) return null;

    return genAnonymousBodyFor((PsiAnonymousClass)parent, editor, file, project);
  }

  public static Runnable genAnonymousBodyFor(PsiAnonymousClass parent,
                                             final Editor editor,
                                             final PsiFile file,
                                             final Project project) {
    try {
      CodeStyleManager.getInstance(project).reformat(parent);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
    int offset = parent.getTextRange().getEndOffset() - 1;
    editor.getCaretModel().moveToOffset(offset);
    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    editor.getSelectionModel().removeSelection();

    return new Runnable() {
      @Override
      public void run(){
        CommandProcessor.getInstance().executeCommand(project, new Runnable() {
          @Override
          public void run() {
            PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
            final PsiAnonymousClass aClass = PsiTreeUtil.findElementOfClassAtOffset(file, editor.getCaretModel().getOffset(), PsiAnonymousClass.class, false);
            if (aClass == null) return;

            final Collection<CandidateInfo> candidatesToImplement = OverrideImplementUtil.getMethodsToOverrideImplement(aClass, true);
            for (Iterator<CandidateInfo> iterator = candidatesToImplement.iterator(); iterator.hasNext(); ) {
              final CandidateInfo candidate = iterator.next();
              final PsiElement element = candidate.getElement();
              if (element instanceof PsiMethod && ((PsiMethod)element).hasModifierProperty(PsiModifier.DEFAULT)) {
                iterator.remove();
              }
            }
            boolean invokeOverride = candidatesToImplement.isEmpty();
            if (invokeOverride){
              OverrideImplementUtil.chooseAndOverrideOrImplementMethods(project, editor, aClass, false);
            }
            else{
              ApplicationManager.getApplication().runWriteAction(new Runnable() {
                @Override
                public void run() {
                  try{
                    List<PsiMethod> methods = OverrideImplementUtil.overrideOrImplementMethodCandidates(aClass, candidatesToImplement, false);
                    List<PsiGenerationInfo<PsiMethod>> prototypes = OverrideImplementUtil.convert2GenerationInfos(methods);
                    List<PsiGenerationInfo<PsiMethod>> resultMembers = GenerateMembersUtil.insertMembersBeforeAnchor(aClass, null, prototypes);
                    resultMembers.get(0).positionCaret(editor, true);
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
}
