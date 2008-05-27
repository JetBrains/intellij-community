package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.AnalysisCanceledException;
import com.intellij.psi.controlFlow.ControlFlow;
import com.intellij.psi.controlFlow.ControlFlowUtil;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.changeSignature.ChangeSignatureProcessor;
import com.intellij.refactoring.changeSignature.OverriderUsageInfo;
import com.intellij.refactoring.changeSignature.ParameterInfo;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class MethodReturnBooleanFix implements IntentionAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.MethodReturnBooleanFix");

  private final PsiMethod myMethod;
  private final PsiType myReturnType;

  public MethodReturnBooleanFix(final PsiMethod method, final PsiType returnType) {
    myMethod = method;
    myReturnType = returnType;
  }

  @NotNull
  public String getText() {
    return QuickFixBundle.message("fix.return.type.text",
                                  myMethod.getName(),
                                  myReturnType.getCanonicalText());
  }

  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("fix.return.type.family");
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return myMethod != null
        && myMethod.isValid()
        && myMethod.getManager().isInProject(myMethod)
        && myReturnType != null
        && myReturnType.isValid()
        && !TypeConversionUtil.isNullType(myReturnType)
        && myMethod.getReturnType() != null
        && !Comparing.equal(myReturnType, myMethod.getReturnType());
  }

  public boolean startInWriteAction() {
    return true;
  }

  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file) {
    if (!CodeInsightUtilBase.prepareFileForWrite(myMethod.getContainingFile())) return;

    final List<PsiMethod> affectedMethods = changeReturnType(myMethod, myReturnType);

    PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
    final SourceMethodSelector returnSelector = new SourceMethodSelector(myMethod);
    final ReturnStatementAdder adder = new ReturnStatementAdder(factory, myReturnType, returnSelector);

    for (PsiMethod method : affectedMethods) {
      adder.addReturnForMethod(file, method);
    }

    final PsiReturnStatement latestReturn = returnSelector.getReturnStatement();
    if (latestReturn != null) {
      selectReturnValueInEditor(latestReturn, getEditorForMethod(project, editor, latestReturn.getContainingFile()));
    }
  }

  private static class SourceMethodSelector implements GeneratedReturnSelector {
    private final PsiMethod mySourceMethod;
    private PsiReturnStatement myReturnStatement;

    private SourceMethodSelector(final PsiMethod sourceMethod) {
      mySourceMethod = sourceMethod;
    }

    public void accept(final PsiReturnStatement statement, final PsiMethod method) {
      if ((mySourceMethod.equals(method)) && (statement != null)) {
        myReturnStatement = statement;
      }
    }

    public PsiReturnStatement getReturnStatement() {
      return myReturnStatement;
    }
  }

  /**
   * selects which of generated / corrected return statements to be selected in editor after operation
   * only latest return statements inside methods are passed
   */
  private interface GeneratedReturnSelector {
    void accept(final PsiReturnStatement statement, final PsiMethod method);
  }

  // to clearly separate data
  private static class ReturnStatementAdder {
    private final PsiElementFactory factory;
    private final PsiType myTargetType;
    private final GeneratedReturnSelector mySelector;

    private ReturnStatementAdder(@NotNull final PsiElementFactory factory, @NotNull final PsiType targetType,
                                 @NotNull final GeneratedReturnSelector selector) {
      this.factory = factory;
      myTargetType = targetType;
      mySelector = selector;
    }

    public void addReturnForMethod(final PsiFile file, final PsiMethod method) {
      final PsiModifierList modifiers = method.getModifierList();
      if ((modifiers.hasModifierProperty(PsiModifier.ABSTRACT)) || (method.getBody() == null)) {
        return;
      }

      try {
        final ConvertReturnStatementsVisitor visitor = new ConvertReturnStatementsVisitor(factory, method, myTargetType);

        final ControlFlow controlFlow = HighlightControlFlowUtil.getControlFlowNoConstantEvaluate(method.getBody());
        if (ControlFlowUtil.checkReturns(controlFlow, visitor)) {
          // extra return statement not needed
          // get latest modified return statement and select...
          mySelector.accept(visitor.getLatestReturn(), method);
          return;
        }

        mySelector.accept(visitor.createReturnInLastStatement(), method);
      }
      catch (AnalysisCanceledException e) {
        LOG.error(e);
        return;
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }

      if (method.getContainingFile() != file) {
        UndoUtil.markPsiFileForUndo(file);
      }
    }
  }

  private Editor getEditorForMethod(@NotNull final Project project, final Editor editor, final PsiFile file) {
    if (myMethod.getContainingFile() != file) {
      OpenFileDescriptor descriptor = new OpenFileDescriptor(project, myMethod.getContainingFile().getVirtualFile());
      return FileEditorManager.getInstance(project).openTextEditor(descriptor, true);
    }
    return editor;
  }

  @Nullable
  private static PsiMethod[] getChangeRoots(final PsiMethod method) {
    final PsiMethod[] methods = method.findDeepestSuperMethods();

    if (methods.length > 0) {
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        return methods;
      }
      final String methodName = PsiFormatUtil.formatMethod(method, PsiSubstitutor.EMPTY, PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_TYPE |
              PsiFormatUtil.SHOW_PARAMETERS | PsiFormatUtil.TYPE_AFTER, PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_TYPE | PsiFormatUtil.TYPE_AFTER);
      final int result = Messages.showYesNoCancelDialog(QuickFixBundle.message("quickfix.retun.type.void.to.boolean.inherited.warning.text",
              method.getContainingClass().getName() + "." + methodName),
              QuickFixBundle.message("quickfix.retun.type.void.to.boolean.inherited.warning.title"), Messages.getQuestionIcon());
      if (2 == result) {
        // cancel
        return null;
      } else if (0 == result) {
        return methods;
      }
    }
    // no - only base
    return new PsiMethod[] {method};
  }

  private static List<PsiMethod> changeReturnType(final PsiMethod method, final PsiType returnType) {
    final PsiMethod[] methods = getChangeRoots(method);
    if (methods == null) {
      // canceled
      return Collections.emptyList();
    }

    final MethodSignatureChangeVisitor methodSignatureChangeVisitor = new MethodSignatureChangeVisitor();
    for (PsiMethod targetMethod : methods) {
      methodSignatureChangeVisitor.addBase(targetMethod);
      ChangeSignatureProcessor processor = new UsagesAwareChangeSignatureProcessor(method.getProject(), targetMethod,
                                                                        false, null,
                                                                        method.getName(),
                                                                        returnType,
                                                                        RemoveUnusedParameterFix.getNewParametersInfo(method, null),
                                                                        methodSignatureChangeVisitor);
      processor.run();
    }

    return methodSignatureChangeVisitor.getAffectedMethods();
  }

  private static class MethodSignatureChangeVisitor implements UsageVisitor {
    private final List<PsiMethod> myAffectedMethods;

    private MethodSignatureChangeVisitor() {
      myAffectedMethods = new ArrayList<PsiMethod>();
    }

    public void addBase(final PsiMethod baseMethod) {
      myAffectedMethods.add(baseMethod);
    }

    public void visit(final UsageInfo usage) {
      if (usage instanceof OverriderUsageInfo) {
        myAffectedMethods.add(((OverriderUsageInfo) usage).getElement());
      }
    }

    public List<PsiMethod> getAffectedMethods() {
      return myAffectedMethods;
    }

    public void preprocessCovariantOverriders(final List<UsageInfo> covariantOverriderInfos) {
      for (Iterator<UsageInfo> usageInfoIterator = covariantOverriderInfos.iterator(); usageInfoIterator.hasNext();) {
        final UsageInfo info = usageInfoIterator.next();
        if (info instanceof OverriderUsageInfo) {
          final OverriderUsageInfo overrideUsage = (OverriderUsageInfo) info;
          if (myAffectedMethods.contains(overrideUsage.getElement())) {
            usageInfoIterator.remove();
          }
        }
      }
    }
  }

  private interface UsageVisitor {
    void visit(final UsageInfo usage);
    void preprocessCovariantOverriders(final List<UsageInfo> covariantOverriderInfos);
  }

  private static class UsagesAwareChangeSignatureProcessor extends ChangeSignatureProcessor {
    private final UsageVisitor myUsageVisitor;

    private UsagesAwareChangeSignatureProcessor(final Project project, final PsiMethod method, final boolean generateDelegate,
                                                @Modifier final String newVisibility, final String newName, final PsiType newType,
                                                @NotNull final ParameterInfo[] parameterInfo, final UsageVisitor usageVisitor) {
      super(project, method, generateDelegate, newVisibility, newName, newType, parameterInfo);
      myUsageVisitor = usageVisitor;
    }

    protected void preprocessCovariantOverriders(final List<UsageInfo> covariantOverriderInfos) {
      myUsageVisitor.preprocessCovariantOverriders(covariantOverriderInfos);
    }

    protected void performRefactoring(final UsageInfo[] usages) {
      super.performRefactoring(usages);

      for (UsageInfo usage : usages) {
        myUsageVisitor.visit(usage);
      }
    }
  }

  private static void selectReturnValueInEditor(final PsiReturnStatement returnStatement, final Editor editor) {
    TextRange range = returnStatement.getReturnValue().getTextRange();
    int offset = range.getStartOffset();

    editor.getCaretModel().moveToOffset(offset);
    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    editor.getSelectionModel().setSelection(range.getEndOffset(), range.getStartOffset());
  }
}
