// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.inline;

import com.intellij.history.LocalHistory;
import com.intellij.history.LocalHistoryAction;
import com.intellij.lang.Language;
import com.intellij.lang.findUsages.DescriptiveNameUtil;
import com.intellij.lang.refactoring.InlineHandler;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.module.UnloadedModuleDescription;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiCall;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiCompiledElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiReturnStatement;
import com.intellij.psi.controlFlow.AnalysisCanceledException;
import com.intellij.psi.controlFlow.ConditionalThrowToInstruction;
import com.intellij.psi.controlFlow.ControlFlow;
import com.intellij.psi.controlFlow.ControlFlowFactory;
import com.intellij.psi.controlFlow.EmptyInstruction;
import com.intellij.psi.controlFlow.GoToInstruction;
import com.intellij.psi.controlFlow.Instruction;
import com.intellij.psi.controlFlow.LocalsControlFlowPolicy;
import com.intellij.psi.controlFlow.ThrowToInstruction;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.listeners.RefactoringEventData;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.NonCodeUsageInfo;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static com.intellij.openapi.util.NlsContexts.DialogMessage;
import static com.intellij.refactoring.inline.InlineMethodProcessorUtil.*;

public class InlineMethodProcessor extends BaseRefactoringProcessor {
  private static final Logger LOG = Logger.getInstance(InlineMethodProcessor.class);

  private PsiMethod myMethod;
  private PsiReference myReference;
  private final Editor myEditor;
  private final boolean myInlineThisOnly;
  private final boolean mySearchInComments;
  private final boolean mySearchForTextOccurrences;
  private final boolean myDeleteTheDeclaration;
  private final Function<PsiReference, InlineTransformer> myTransformerChooser;

  private final String myDescriptiveName;
  @SuppressWarnings("LeakableMapKey") //short living refactoring
  private Map<Language, InlineHandler.Inliner> myInliners;

  public InlineMethodProcessor(@NotNull Project project,
                               @NotNull PsiMethod method,
                               @Nullable PsiReference reference,
                               Editor editor,
                               boolean isInlineThisOnly) {
    this(project, method, reference, editor, isInlineThisOnly, false, false, true);
  }

  public InlineMethodProcessor(@NotNull Project project,
                             @NotNull PsiMethod method,
                             @Nullable PsiReference reference,
                             Editor editor,
                             boolean isInlineThisOnly,
                             boolean searchInComments,
                             boolean searchForTextOccurrences) {
    this(project, method, reference, editor, isInlineThisOnly, searchInComments, searchForTextOccurrences, true);
  }

  public InlineMethodProcessor(@NotNull Project project,
                               @NotNull PsiMethod method,
                               @Nullable PsiReference reference,
                               Editor editor,
                               boolean isInlineThisOnly,
                               boolean searchInComments,
                               boolean searchForTextOccurrences,
                               boolean isDeleteTheDeclaration) {
    super(project);
    myMethod = InlineMethodSpecialization.specialize(method, reference);
    myTransformerChooser = InlineTransformer.getSuitableTransformer(myMethod);
    myReference = reference;
    myEditor = editor;
    myInlineThisOnly = isInlineThisOnly;
    mySearchInComments = searchInComments;
    mySearchForTextOccurrences = searchForTextOccurrences;
    myDeleteTheDeclaration = isDeleteTheDeclaration;

    myDescriptiveName = DescriptiveNameUtil.getDescriptiveName(myMethod);
  }

  private @NotNull InlineMethodContext context() {
    return new InlineMethodContext(myMethod, myReference, myTransformerChooser, myInlineThisOnly, myDeleteTheDeclaration);
  }

  @Override
  protected @NotNull String getCommandName() {
    return RefactoringBundle.message("inline.method.command", myDescriptiveName);
  }

  @Override
  protected @NotNull UsageViewDescriptor createUsageViewDescriptor(UsageInfo @NotNull [] usages) {
    return new InlineViewDescriptor(myMethod);
  }

  @Override
  protected UsageInfo @NotNull [] findUsages() {
    return InlineMethodProcessorUtil.findUsages(context(), myRefactoringScope, mySearchInComments, mySearchForTextOccurrences);
  }

  @Override
  protected boolean isPreviewUsages(UsageInfo @NotNull [] usages) {
    for (UsageInfo usage : usages) {
      if (usage instanceof NonCodeUsageInfo) return true;
    }
    return super.isPreviewUsages(usages);
  }

  @Override
  protected Set<UnloadedModuleDescription> computeUnloadedModulesFromUseScope(UsageViewDescriptor descriptor) {
    if (myInlineThisOnly) {
      return Collections.emptySet();
    }
    return super.computeUnloadedModulesFromUseScope(descriptor);
  }

  @Override
  protected void refreshElements(PsiElement @NotNull [] elements) {
    boolean condition = elements.length == 1 && elements[0] instanceof PsiMethod;
    LOG.assertTrue(condition);
    myMethod = (PsiMethod)elements[0];
  }

  @Override
  protected boolean preprocessUsages(@NotNull Ref<UsageInfo[]> refUsages) {
    if (!myInlineThisOnly && checkReadOnly()) {
      if (!CommonRefactoringUtil.checkReadOnlyStatus(myProject, myMethod)) return false;
    }
    final UsageInfo[] usagesIn = refUsages.get();
    final MultiMap<PsiElement, @DialogMessage String> conflicts = new MultiMap<>();

    if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(
      () -> ReadAction.run(() -> collectConflicts(context(), usagesIn, conflicts)),
      RefactoringBundle.message("detecting.possible.conflicts"), true, myProject)) {
      return false;
    }

    //kotlin j2k fails badly if moved under progress
    myInliners = initInliners(context(), usagesIn, conflicts);

    return showConflicts(conflicts, usagesIn);
  }

  private boolean checkReadOnly() {
    return myMethod.isWritable() || myMethod instanceof PsiCompiledElement;
  }

  /**
   * @deprecated Use {@link InlineMethodProcessorUtil#addInaccessibleMemberConflicts} instead.
   */
  @Deprecated
  public static void addInaccessibleMemberConflicts(PsiMethod method,
                                                    UsageInfo[] usages,
                                                    ReferencedElementsCollector collector,
                                                    MultiMap<PsiElement, @DialogMessage String> conflicts) {
    InlineMethodProcessorUtil.addInaccessibleMemberConflicts(method, usages, collector, conflicts);
  }

  @Override
  protected void performRefactoring(UsageInfo @NotNull [] usages) {
    RangeMarker position = null;
    if (myEditor != null) {
      final int offset = myEditor.getCaretModel().getOffset();
      position = myEditor.getDocument().createRangeMarker(offset, offset + 1);
    }

    LocalHistoryAction a = LocalHistory.getInstance().startAction(getCommandName());
    try {
      myReference = InlineMethodProcessorUtil.performRefactoring(context(), usages, myInliners);
    }
    finally {
      a.finish();
    }

    if (position != null) {
      if (position.isValid()) {
        myEditor.getCaretModel().moveToOffset(position.getStartOffset());
      }
      position.dispose();
    }
  }

  @Override
  protected String getRefactoringId() {
    return "refactoring.inline.method";
  }

  @Override
  protected RefactoringEventData getBeforeData() {
    final RefactoringEventData data = new RefactoringEventData();
    if (myDeleteTheDeclaration) data.addElement(myMethod);
    return data;
  }

  /**
   * @deprecated Use {@link InlineMethodProcessorUtil#inlineConstructorCall} instead.
   */
  @Deprecated
  public static void inlineConstructorCall(PsiCall constructorCall) {
    InlineMethodProcessorUtil.inlineConstructorCall(constructorCall);
  }

  public void inlineMethodCall(PsiReferenceExpression ref) {
    InlineMethodProcessorUtil.inlineMethodCall(context(), ref);
  }

  /**
   * @deprecated Use {@link InlineMethodProcessorUtil#checkUnableToInsertCodeBlock} instead.
   */
  @Deprecated
  public static @DialogMessage String checkUnableToInsertCodeBlock(PsiCodeBlock methodBody, PsiElement element) {
    return InlineMethodProcessorUtil.checkUnableToInsertCodeBlock(methodBody, element);
  }

  public static boolean checkBadReturns(PsiMethod method) {
    PsiReturnStatement[] returns = PsiUtil.findReturnStatements(method);
    PsiCodeBlock body = method.getBody();
    return checkBadReturns(returns, body);
  }

  public static boolean checkBadReturns(PsiReturnStatement[] returns, PsiCodeBlock body) {
    if (returns.length == 0) return false;
    ControlFlow controlFlow;
    try {
      controlFlow = ControlFlowFactory.getInstance(body.getProject()).getControlFlow(body, new LocalsControlFlowPolicy(body), false);
    }
    catch (AnalysisCanceledException e) {
      return false;
    }
    if (LOG.isDebugEnabled()) {
      LOG.debug("Control flow:");
      LOG.debug(controlFlow.toString());
    }

    List<Instruction> instructions = new ArrayList<>(controlFlow.getInstructions());

    // temporary replace all return's with empty statements in the flow
    for (PsiReturnStatement aReturn : returns) {
      int offset = controlFlow.getStartOffset(aReturn);
      int endOffset = controlFlow.getEndOffset(aReturn);
      while (offset <= endOffset && !(instructions.get(offset) instanceof GoToInstruction)) {
        offset++;
      }
      LOG.assertTrue(instructions.get(offset) instanceof GoToInstruction);
      instructions.set(offset, EmptyInstruction.INSTANCE);
    }

    for (PsiReturnStatement aReturn : returns) {
      int offset = controlFlow.getEndOffset(aReturn);
      while (offset != instructions.size()) {
        Instruction instruction = instructions.get(offset);
        if (instruction instanceof GoToInstruction g) {
          offset = g.offset;
        }
        else if (instruction instanceof ThrowToInstruction t) {
          offset = t.offset;
        }
        else if (instruction instanceof ConditionalThrowToInstruction) {
          // In case of "conditional throw to", control flow will not be altered
          // If exception handler is in method, we will inline it to invocation site
          // If exception handler is at invocation site, execution will continue to get there
          offset++;
        }
        else {
          return true;
        }
      }
    }

    return false;
  }

  @Override
  protected @NotNull Collection<? extends PsiElement> getElementsToWrite(@NotNull UsageViewDescriptor descriptor) {
    if (myInlineThisOnly) {
      return Collections.singletonList(myReference.getElement());
    }
    else {
      if (!checkReadOnly()) return Collections.emptyList();
      return myReference == null ? Collections.singletonList(myMethod) : Arrays.asList(myReference.getElement(), myMethod);
    }
  }
}