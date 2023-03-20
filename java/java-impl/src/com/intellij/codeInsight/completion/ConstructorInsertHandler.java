// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.ExpectedTypesProvider;
import com.intellij.codeInsight.completion.util.MethodParenthesesHandler;
import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.codeInsight.generation.OverrideImplementExploreUtil;
import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.codeInsight.generation.PsiGenerationInfo;
import com.intellij.codeInsight.intention.impl.TypeExpression;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementDecorator;
import com.intellij.codeInsight.lookup.PsiTypeLookupItem;
import com.intellij.codeInsight.template.*;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ThreeState;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

public final class ConstructorInsertHandler implements InsertHandler<LookupElementDecorator<LookupElement>> {
  private static final Logger LOG = Logger.getInstance(ConstructorInsertHandler.class);
  public static final ConstructorInsertHandler SMART_INSTANCE = new ConstructorInsertHandler(true);
  public static final ConstructorInsertHandler BASIC_INSTANCE = new ConstructorInsertHandler(false);
  private final boolean mySmart;

  private ConstructorInsertHandler(boolean smart) {
    mySmart = smart;
  }

  @Override
  public void handleInsert(@NotNull InsertionContext context, @NotNull LookupElementDecorator<LookupElement> item) {
    final LookupElement delegate = item.getDelegate();

    PsiClass psiClass = (PsiClass)item.getObject();
    SmartPsiElementPointer<PsiClass> classPointer = SmartPointerManager.createPointer(psiClass);
    boolean isAbstract = psiClass.hasModifierProperty(PsiModifier.ABSTRACT);

    if (Lookup.REPLACE_SELECT_CHAR == context.getCompletionChar()) {
      JavaClassNameInsertHandler.overwriteTopmostReference(context);
    }

    context.commitDocument();

    OffsetKey insideRef = context.trackOffset(context.getTailOffset(), false);

    PsiElement position = context.getFile().findElementAt(context.getStartOffset());
    if (position == null) return;

    PsiAnonymousClass anonymousClass = PsiTreeUtil.getParentOfType(position, PsiAnonymousClass.class);
    boolean inAnonymous = anonymousClass != null && PsiTreeUtil.isAncestor(anonymousClass.getBaseClassReference(), position, false);
    if (delegate instanceof PsiTypeLookupItem) {
      if (context.getDocument().getTextLength() > context.getTailOffset() &&
          context.getDocument().getCharsSequence().charAt(context.getTailOffset()) == '<') {
        PsiJavaCodeReferenceElement ref = JavaClassNameInsertHandler.findJavaReference(context.getFile(), context.getTailOffset() - 1);
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

    if (item.getDelegate() instanceof JavaPsiClassReferenceElement && classPointer.getElement() != null) {
      PsiTypeLookupItem.addImportForItem(context, classPointer.getElement());
    }


    if (classPointer.getElement() != null) {
      insertParentheses(context, delegate, classPointer.getElement(), !inAnonymous && isAbstract);
    }

    if (inAnonymous) {
      return;
    }

    if (mySmart) {
      FeatureUsageTracker.getInstance().triggerFeatureUsed(JavaCompletionFeatures.AFTER_NEW);
    }
    if (isAbstract) {
      PostprocessReformattingAspect.getInstance(context.getProject()).doPostponedFormatting(context.getFile().getViewProvider());

      final Editor editor = context.getEditor();
      final Document document = editor.getDocument();
      final int offset = context.getTailOffset();

      document.insertString(offset, " {}");
      OffsetKey insideBraces = context.trackOffset(offset + 2, true);

      final PsiFile file = context.getFile();
      PsiDocumentManager.getInstance(file.getProject()).commitDocument(document);
      reformatEnclosingExpressionListAtOffset(file, offset);

      if (promptTypeOrConstructorArgs(context, delegate, insideRef, insideBraces)) return;

      editor.getCaretModel().moveToOffset(context.getOffset(insideBraces));
      context.setLaterRunnable(generateAnonymousBody(editor, file));
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
      promptTypeOrConstructorArgs(context, delegate, insideRef, null);
    }
  }

  private static boolean promptTypeOrConstructorArgs(InsertionContext context, LookupElement delegate, OffsetKey refOffset, @Nullable OffsetKey insideBraces) {
    if (shouldFillTypeArgs(context, delegate) && JavaCompletionUtil.promptTypeArgs(context, context.getOffset(refOffset))) {
      return true;
    }

    PsiMethod constructor = JavaConstructorCallElement.extractCalledConstructor(delegate);
    if (constructor != null && JavaMethodCallElement.startArgumentLiveTemplate(context, constructor)) {
      implementMethodsWhenTemplateIsFinished(context, insideBraces);
      return true;
    }
    return false;
  }

  private static void implementMethodsWhenTemplateIsFinished(InsertionContext context, @Nullable final OffsetKey insideBraces) {
    TemplateState state = TemplateManagerImpl.getTemplateState(context.getEditor());
    if (state != null && insideBraces != null) {
      state.addTemplateStateListener(new TemplateEditingAdapter() {
        @Override
        public void templateFinished(@NotNull Template template, boolean brokenOff) {
          if (!brokenOff) {
            context.getEditor().getCaretModel().moveToOffset(context.getOffset(insideBraces));
            createOverrideRunnable(context.getEditor(), context.getFile(), context.getProject()).run();
          }
        }
      });
    }
  }

  private static boolean shouldFillTypeArgs(InsertionContext context, LookupElement delegate) {
    if (!(delegate instanceof PsiTypeLookupItem) ||
        isRawTypeExpected(context, (PsiTypeLookupItem)delegate) ||
        !((PsiClass)delegate.getObject()).hasTypeParameters()) {
      return false;
    }

    PsiElement position = context.getFile().findElementAt(context.getStartOffset());
    return position != null &&
           ((PsiTypeLookupItem)delegate).calcGenerics(position, context).isEmpty() &&
           context.getCompletionChar() != '(';
  }

  private static void reformatEnclosingExpressionListAtOffset(@NotNull PsiFile file, int offset) {
    final PsiElement elementAtOffset = PsiUtilCore.getElementAtOffset(file, offset);
    PsiExpressionList listToReformat = getEnclosingExpressionList(elementAtOffset.getParent());
    if (listToReformat != null) {
      CodeStyleManager.getInstance(file.getProject()).reformat(listToReformat);
      PostprocessReformattingAspect.getInstance(file.getProject()).doPostponedFormatting();
    }
  }

  @Nullable
  private static PsiExpressionList getEnclosingExpressionList(@NotNull PsiElement element) {
    if (!(element instanceof PsiAnonymousClass)) {
      return null;
    }

    PsiElement e = element.getParent();
    if (e instanceof PsiNewExpression && e.getParent() instanceof PsiExpressionList) {
      return (PsiExpressionList)e.getParent();
    }

    return null;
  }

  static boolean isRawTypeExpected(InsertionContext context, PsiTypeLookupItem delegate) {
    PsiNewExpression newExpr =
      PsiTreeUtil.findElementOfClassAtOffset(context.getFile(), context.getStartOffset(), PsiNewExpression.class, false);
    if (newExpr != null) {
      for (ExpectedTypeInfo info : ExpectedTypesProvider.getExpectedTypes(newExpr, true)) {
        PsiType expected = info.getDefaultType();
        if (expected.isAssignableFrom(delegate.getType())) {
          if (expected instanceof PsiClassType && ((PsiClassType)expected).isRaw()) {
            return true;
          }
        }
      }
    }
    return false;
  }

  public static boolean insertParentheses(@NotNull InsertionContext context,
                                          @NotNull LookupElement delegate,
                                          @NotNull PsiClass psiClass,
                                          boolean forAnonymous) {
    if (context.getCompletionChar() == '[') {
      return false;
    }

    PsiMethod constructor = JavaConstructorCallElement.extractCalledConstructor(delegate);

    final PsiElement place = context.getFile().findElementAt(context.getStartOffset());
    assert place != null;
    ThreeState hasParams = constructor != null ? ThreeState.fromBoolean(!constructor.getParameterList().isEmpty())
                                               : hasConstructorParameters(psiClass, place);

    RangeMarker refEnd = context.getDocument().createRangeMarker(context.getTailOffset(), context.getTailOffset());

    JavaCompletionUtil.insertParentheses(context, delegate, false, hasParams, forAnonymous);

    if (constructor != null) {
      PsiCallExpression call = JavaMethodCallElement.findCallAtOffset(context, refEnd.getStartOffset());
      if (call != null) {
        CompletionMemory.registerChosenMethod(constructor, call);
      }
    }

    return true;
  }

  static ThreeState hasConstructorParameters(PsiClass psiClass, @NotNull PsiElement place) {
    PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(place.getProject()).getResolveHelper();
    return MethodParenthesesHandler.hasParameters(ContainerUtil.filter(psiClass.getConstructors(),
                                                                       c -> resolveHelper.isAccessible(c, place, null)));
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

    final PsiReferenceParameterList parameterList = parent.getBaseClassReference().getParameterList();
    final PsiTypeElement[] parameters = parameterList != null ? parameterList.getTypeParameterElements() : null;
    final PsiElement newExpr = parent.getParent();
    if (newExpr != null && PsiTypesUtil.getExpectedTypeByParent(newExpr) == null && shouldStartTypeTemplate(parameters)) {
      startTemplate(parent, editor, createOverrideRunnable(editor, file, project), parameters);
      return null;
    }

    return createOverrideRunnable(editor, file, project);
  }

  private static Runnable createOverrideRunnable(final Editor editor, final PsiFile file, final Project project) {
    return () -> {
      TemplateManager.getInstance(project).finishTemplate(editor);
      if (DumbService.getInstance(project).isDumb()) return;

      int offset = editor.getCaretModel().getOffset();
      RangeMarker marker = editor.getDocument().createRangeMarker(offset, offset);
      record OverrideContext(@NotNull PsiAnonymousClass aClass, @NotNull Collection<CandidateInfo> candidatesToImplement) {}
      ReadAction.nonBlocking(() -> {
        PsiAnonymousClass anonymousClass = PsiTreeUtil.findElementOfClassAtOffset(file, marker.getStartOffset(), PsiAnonymousClass.class, false);
        if (anonymousClass == null) return null;
        final Collection<CandidateInfo> candidatesToImplement = OverrideImplementExploreUtil.getMethodsToOverrideImplement(anonymousClass, true);
        candidatesToImplement.removeIf(
            candidate -> candidate.getElement() instanceof PsiMethod method && method.hasModifierProperty(PsiModifier.DEFAULT));
        return new OverrideContext(anonymousClass, candidatesToImplement);
      }).withDocumentsCommitted(project)
        .finishOnUiThread(ModalityState.NON_MODAL, context -> {
        marker.dispose();
        if (context == null) return;
        CommandProcessor.getInstance().executeCommand(project, () -> {
          PsiAnonymousClass anonymousClass = context.aClass;
          boolean invokeOverride = context.candidatesToImplement.isEmpty();
          if (invokeOverride) {
            OverrideImplementUtil.chooseAndOverrideOrImplementMethods(project, editor, anonymousClass, false);
          }
          else {
            ApplicationManagerEx.getApplicationEx()
              .runWriteActionWithCancellableProgressInDispatchThread(getCommandName(), project, editor.getComponent(), indicator -> {
                try {
                  List<PsiMethod> methods = OverrideImplementUtil.overrideOrImplementMethodCandidates(
                    anonymousClass, context.candidatesToImplement, false);
                  List<PsiGenerationInfo<PsiMethod>> prototypes = OverrideImplementUtil.convert2GenerationInfos(methods);
                  List<PsiGenerationInfo<PsiMethod>> resultMembers =
                    GenerateMembersUtil.insertMembersBeforeAnchor(anonymousClass, null, prototypes);
                  resultMembers.get(0).positionCaret(editor, true);
                }
                catch (IncorrectOperationException ioe) {
                  LOG.error(ioe);
                }
              });
          }
        }, getCommandName(), getCommandName(), UndoConfirmationPolicy.DEFAULT, editor.getDocument());
      }).submit(AppExecutorUtil.getAppExecutorService());
    };
  }

  @Contract("null -> false")
  private static boolean shouldStartTypeTemplate(PsiTypeElement[] parameters) {
    if (parameters != null) {
      for (PsiTypeElement parameter : parameters) {
        if (parameter.getType().equalsToText(CommonClassNames.JAVA_LANG_OBJECT)) {
          return true;
        }
      }
    }
    return false;
  }

  private static void startTemplate(final PsiAnonymousClass aClass, final Editor editor, final Runnable runnable, final PsiTypeElement @NotNull [] parameters) {
    final Project project = aClass.getProject();
    WriteCommandAction.writeCommandAction(project).withName(getCommandName()).withGroupId(getCommandName()).run(() -> {
      PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.getDocument());
      editor.getCaretModel().moveToOffset(aClass.getTextOffset());
      final TemplateBuilderImpl templateBuilder = (TemplateBuilderImpl)TemplateBuilderFactory.getInstance().createTemplateBuilder(aClass);
      for (int i = 0; i < parameters.length; i++) {
        PsiTypeElement parameter = parameters[i];
        templateBuilder.replaceElement(parameter, "param" + i, new TypeExpression(project, new PsiType[]{parameter.getType()}), true);
      }
      Template template = templateBuilder.buildInlineTemplate();
      TemplateManager.getInstance(project).startTemplate(editor, template, false, null, new TemplateEditingAdapter() {
        @Override
        public void templateFinished(@NotNull Template template, boolean brokenOff) {
          if (!brokenOff) {
            runnable.run();
          }
        }
      });
    });
  }

  private static @NlsContexts.Command String getCommandName() {
    return JavaBundle.message("completion.smart.type.generate.anonymous.body");
  }
}
