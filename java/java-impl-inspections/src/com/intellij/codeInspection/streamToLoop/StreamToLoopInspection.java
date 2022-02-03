// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.streamToLoop;

import com.intellij.codeInspection.*;
import com.intellij.codeInspection.redundantCast.RemoveRedundantCastUtil;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.source.PsiImmediateClassType;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.RedundantCastUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.*;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;


public class StreamToLoopInspection extends AbstractBaseJavaLocalInspectionTool {
  private static final Logger LOG = Logger.getInstance(StreamToLoopInspection.class);

  // To quickly filter out most of the non-interesting method calls
  private static final Set<String> SUPPORTED_TERMINALS = ContainerUtil.set(
    "count", "sum", "summaryStatistics", "reduce", "collect", "findFirst", "findAny", "anyMatch", "allMatch", "noneMatch", "toArray",
    "average", "forEach", "forEachOrdered", "min", "max", "toList", "toSet", "toImmutableList", "toImmutableSet");

  private static final CallMatcher ITERABLE_FOREACH = CallMatcher.instanceCall(
    CommonClassNames.JAVA_LANG_ITERABLE, "forEach").parameterTypes("java.util.function.Consumer");
  private static final CallMatcher MAP_FOREACH = CallMatcher.instanceCall(
    CommonClassNames.JAVA_UTIL_MAP, "forEach").parameterTypes("java.util.function.BiConsumer");

  @SuppressWarnings("PublicField")
  public boolean SUPPORT_UNKNOWN_SOURCES = false;

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(JavaBundle.message("checkbox.iterate.unknown.stream.sources.via.stream.iterator"), this, "SUPPORT_UNKNOWN_SOURCES");
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (!PsiUtil.isLanguageLevel8OrHigher(holder.getFile())) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    return new JavaElementVisitor() {
      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression call) {
        super.visitMethodCallExpression(call);
        PsiReferenceExpression expression = call.getMethodExpression();
        PsiElement nameElement = expression.getReferenceNameElement();
        if (nameElement == null || !SUPPORTED_TERMINALS.contains(nameElement.getText())) return;
        if (!CodeBlockSurrounder.canSurround(call)) return;
        PsiMethod method = call.resolveMethod();
        if(method == null) return;
        PsiClass aClass = method.getContainingClass();
        if (InheritanceUtil.isInheritor(aClass, CommonClassNames.JAVA_UTIL_STREAM_BASE_STREAM)) {
          if (extractOperations(ChainVariable.STUB, call, SUPPORT_UNKNOWN_SOURCES) != null) {
            register(call, nameElement, JavaBundle.message("stream.to.loop.inspection.message.replace.stream.api.chain.with.loop"));
          }
        }
        else if (extractIterableForEach(call) != null || extractMapForEach(call) != null) {
          register(call, nameElement, JavaBundle.message("stream.to.loop.inspection.message.replace.foreach.call.with.loop"));
        }
      }

      private void register(PsiMethodCallExpression call, PsiElement nameElement, @InspectionMessage String message) {
        TextRange range;
        if (isOnTheFly && InspectionProjectProfileManager.isInformationLevel(getShortName(), call)) {
          range = new TextRange(0, call.getTextLength());
        }
        else {
          range = nameElement.getTextRange().shiftRight(-call.getTextOffset());
        }
        holder.registerProblem(call, range, message, new ReplaceStreamWithLoopFix(message));
      }
    };
  }

  @Nullable
  static Operation createOperationFromCall(ChainVariable outVar, PsiMethodCallExpression call, boolean supportUnknownSources) {
    PsiMethod method = call.resolveMethod();
    if(method == null) return null;
    PsiClass aClass = method.getContainingClass();
    if(aClass == null) return null;
    PsiExpression[] args = call.getArgumentList().getExpressions();
    String name = method.getName();
    String className = aClass.getQualifiedName();
    if(className == null) return null;
    PsiType callType = call.getType();
    if(callType == null) return null;
    if(InheritanceUtil.isInheritor(aClass, CommonClassNames.JAVA_UTIL_STREAM_BASE_STREAM) &&
       !method.getModifierList().hasExplicitModifier(PsiModifier.STATIC)) {
      PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
      if(qualifier != null) {
        PsiType elementType = StreamApiUtil.getStreamElementType(qualifier.getType());
        if (!isValidElementType(elementType, call, false)) return null;
        Operation op = Operation.createIntermediate(name, args, outVar, elementType, supportUnknownSources);
        if (op != null) return op;
        op = TerminalOperation.createTerminal(name, args, elementType, callType, ExpressionUtils.isVoidContext(call));
        if (op != null) return op;
      }
      return null;
    }
    return SourceOperation.createSource(call, supportUnknownSources);
  }

  private static boolean isValidElementType(PsiType elementType, PsiElement context, boolean allowRaw) {
    if(elementType == null || (!allowRaw && (elementType instanceof PsiClassType) && ((PsiClassType)elementType).isRaw())) {
      // Raw type in any stream step is not supported
      return false;
    }
    if(elementType instanceof PsiImmediateClassType) {
      PsiResolveHelper helper = PsiResolveHelper.SERVICE.getInstance(context.getProject());
      if (helper.resolveReferencedClass(elementType.getCanonicalText(), context) == null) {
        return false;
      }
    }
    return true;
  }

  @Nullable
  static List<OperationRecord> extractIterableForEach(PsiMethodCallExpression terminalCall) {
    if (!ITERABLE_FOREACH.test(terminalCall) || !ExpressionUtils.isVoidContext(terminalCall)) return null;
    PsiExpression qualifier = ExpressionUtils.getEffectiveQualifier(terminalCall.getMethodExpression());
    if (qualifier == null) return null;
    // Do not visit this path if some class implements both Iterable and Stream
    PsiType type = qualifier.getType();
    if (InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_UTIL_STREAM_BASE_STREAM)) return null;
    PsiExpression arg = terminalCall.getArgumentList().getExpressions()[0];
    FunctionHelper fn = FunctionHelper.create(arg, 1, true);
    if (fn == null) return null;
    PsiType elementType = PsiUtil.substituteTypeParameter(type, CommonClassNames.JAVA_LANG_ITERABLE, 0, false);
    if (!isValidElementType(elementType, terminalCall, true)) return null;
    elementType = GenericsUtil.getVariableTypeByExpressionType(elementType);
    TerminalOperation terminal = new TerminalOperation.ForEachTerminalOperation(fn);
    SourceOperation source = new SourceOperation.ForEachSource(qualifier);
    OperationRecord terminalRecord = new OperationRecord();
    OperationRecord sourceRecord = new OperationRecord();
    terminalRecord.myOperation = terminal;
    sourceRecord.myOperation = source;
    sourceRecord.myOutVar = terminalRecord.myInVar = new ChainVariable(elementType);
    sourceRecord.myInVar = terminalRecord.myOutVar = ChainVariable.STUB;
    return Arrays.asList(sourceRecord, terminalRecord);
  }

  @Nullable
  static List<OperationRecord> extractMapForEach(PsiMethodCallExpression terminalCall) {
    if (!MAP_FOREACH.test(terminalCall) || !ExpressionUtils.isVoidContext(terminalCall)) return null;
    PsiExpression qualifier = ExpressionUtils.getEffectiveQualifier(terminalCall.getMethodExpression());
    if (qualifier == null) return null;
    // Do not visit this path if some class implements both Map and Stream
    PsiType type = qualifier.getType();
    if (InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_UTIL_STREAM_BASE_STREAM)) return null;
    PsiExpression arg = terminalCall.getArgumentList().getExpressions()[0];
    FunctionHelper fn = FunctionHelper.create(arg, 2, true);
    if (fn == null) return null;
    PsiType keyType = PsiUtil.substituteTypeParameter(type, CommonClassNames.JAVA_UTIL_MAP, 0, false);
    PsiType valueType = PsiUtil.substituteTypeParameter(type, CommonClassNames.JAVA_UTIL_MAP, 1, false);
    if (!isValidElementType(keyType, terminalCall, true) || !isValidElementType(valueType, terminalCall, true)) return null;
    Project project = terminalCall.getProject();
    JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    PsiClass entryClass = facade.findClass(CommonClassNames.JAVA_UTIL_MAP_ENTRY, terminalCall.getResolveScope());
    if (entryClass == null || entryClass.getTypeParameters().length != 2) return null;
    PsiType entryType = JavaPsiFacade.getElementFactory(project).createType(entryClass, keyType, valueType);
    keyType = GenericsUtil.getVariableTypeByExpressionType(keyType);
    valueType = GenericsUtil.getVariableTypeByExpressionType(valueType);
    TerminalOperation terminal = new TerminalOperation.MapForEachTerminalOperation(fn, keyType, valueType);
    SourceOperation source = new SourceOperation.ForEachSource(qualifier, true);
    OperationRecord terminalRecord = new OperationRecord();
    OperationRecord sourceRecord = new OperationRecord();
    terminalRecord.myOperation = terminal;
    sourceRecord.myOperation = source;
    sourceRecord.myOutVar = terminalRecord.myInVar = new ChainVariable(entryType);
    sourceRecord.myInVar = terminalRecord.myOutVar = ChainVariable.STUB;
    return Arrays.asList(sourceRecord, terminalRecord);
  }

  @Nullable
  static List<OperationRecord> extractOperations(ChainVariable outVar,
                                                 PsiMethodCallExpression terminalCall,
                                                 boolean supportUnknownSources) {
    List<OperationRecord> operations = new ArrayList<>();
    PsiMethodCallExpression currentCall = terminalCall;
    ChainVariable lastVar = outVar;
    Operation next = null;
    while(true) {
      Operation op = createOperationFromCall(lastVar, currentCall, supportUnknownSources);
      if(op == null) return null;
      if(next != null) {
        Operation combined = op.combineWithNext(next);
        if (combined != null) {
          op = combined;
          operations.remove(operations.size() - 1);
        }
      }
      OperationRecord or = new OperationRecord();
      or.myOperation = op;
      or.myOutVar = lastVar;
      operations.add(or);
      if(op instanceof SourceOperation) {
        or.myInVar = ChainVariable.STUB;
        Collections.reverse(operations);
        return operations;
      }
      currentCall = MethodCallUtils.getQualifierMethodCall(currentCall);
      if(currentCall == null) return null;
      if(op.changesVariable()) {
        PsiType type = StreamApiUtil.getStreamElementType(currentCall.getType());
        if(type == null) return null;
        lastVar = new ChainVariable(type);
      }
      or.myInVar = lastVar;
      next = op;
    }
  }

  @Contract("null -> null")
  @Nullable
  static TerminalOperation getTerminal(List<? extends OperationRecord> operations) {
    if (operations == null || operations.isEmpty()) return null;
    OperationRecord record = operations.get(operations.size()-1);
    if(record.myOperation instanceof TerminalOperation) {
      return (TerminalOperation)record.myOperation;
    }
    return null;
  }

  static class ReplaceStreamWithLoopFix implements LocalQuickFix {
    private final @IntentionName String myMessage;

    ReplaceStreamWithLoopFix(@IntentionName String message) {
      myMessage = message;
    }

    @NotNull
    @Override
    public String getName() {
      return myMessage;
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return JavaBundle.message("quickfix.family.replace.stream.api.chain.with.loop");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiElement element = descriptor.getStartElement();
      if(!(element instanceof PsiMethodCallExpression)) return;
      PsiMethodCallExpression terminalCall = (PsiMethodCallExpression)element;
      CodeBlockSurrounder surrounder = CodeBlockSurrounder.forExpression(terminalCall);
      if (surrounder == null) return;
      CodeBlockSurrounder.SurroundResult surroundResult = surrounder.surround();
      terminalCall = (PsiMethodCallExpression)surroundResult.getExpression();
      PsiType resultType = terminalCall.getType();
      if (resultType == null) return;
      List<OperationRecord> operations = extractOperations(ChainVariable.STUB, terminalCall, true);
      if (operations == null) {
        operations = extractIterableForEach(terminalCall);
      }
      if (operations == null) {
        operations = extractMapForEach(terminalCall);
      }
      TerminalOperation terminal = getTerminal(operations);
      if (terminal == null) return;
      PsiStatement statement = surroundResult.getAnchor();
      CommentTracker ct = new CommentTracker();
      try {
        StreamToLoopReplacementContext context =
          new StreamToLoopReplacementContext(statement, operations, terminalCall, ct);
        registerVariables(operations, context);
        String replacement = "";
        for (OperationRecord or : StreamEx.ofReversed(operations)) {
          replacement = or.myOperation.wrap(or.myInVar, or.myOutVar, replacement, context);
        }
        PsiElement firstAdded = null;
        PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
        for (PsiStatement addedStatement : ((PsiBlockStatement)factory.createStatementFromText("{" + replacement + "}", statement))
          .getCodeBlock().getStatements()) {
          PsiElement res = addStatement(project, statement, addedStatement);
          if (firstAdded == null) {
            firstAdded = res;
          }
        }
        PsiElement result = context.makeFinalReplacement();
        if(result != null) {
          result = normalize(project, result);
          if (firstAdded == null) {
            firstAdded = result;
          }
        }
        if (firstAdded != null) {
          ct.insertCommentsBefore(firstAdded);
        }
      }
      catch (Exception ex) {
        LOG.error("Error converting Stream to loop", ex, new Attachment("Stream_code.txt", terminalCall.getText()));
      }
    }

    private static PsiElement addStatement(@NotNull Project project, PsiStatement statement, PsiStatement context) {
      PsiElement element = statement.getParent().addBefore(context, statement);
      return normalize(project, element);
    }

    private static PsiElement normalize(@NotNull Project project, PsiElement element) {
      element = JavaCodeStyleManager.getInstance(project).shortenClassReferences(element);
      RemoveRedundantTypeArgumentsUtil.removeRedundantTypeArguments(element);
      RedundantCastUtil.getRedundantCastsInside(element).forEach(RemoveRedundantCastUtil::removeCast);
      return element;
    }

    private static StreamEx<OperationRecord> allOperations(List<OperationRecord> operations) {
      return StreamEx.of(operations)
        .flatMap(or -> or.myOperation.nestedOperations().append(or));
    }

    private static void registerVariables(List<OperationRecord> operations, StreamToLoopReplacementContext context) {
      allOperations(operations).forEach(or -> or.myOperation.preprocessVariables(context, or.myInVar, or.myOutVar));
      allOperations(operations).map(or -> or.myOperation).forEach(op -> op.registerReusedElements(context::registerReusedElement));
      allOperations(operations).map(or -> or.myInVar).distinct().forEach(var -> var.register(context));
    }
  }

  enum ResultKind {
    /**
     * Result variable is used as complete stream result and not modified after declaration
     * E.g. {@code Collectors.toList()} creates such result.
     */
    FINAL,
    /**
     * Result variable is used as complete stream result, but could be modified in loop
     * E.g. {@code Stream.count()} creates such result.
     */
    NON_FINAL,
    /**
     * Result variable is not directly used as stream result: additional transformations are possible.
     */
    UNKNOWN
  }

  static class OperationRecord {
    Operation myOperation;
    ChainVariable myInVar, myOutVar;
  }
}
