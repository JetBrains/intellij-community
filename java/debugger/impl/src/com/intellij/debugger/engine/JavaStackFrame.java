// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine;

import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.evaluation.TextWithImports;
import com.intellij.debugger.engine.evaluation.TextWithImportsImpl;
import com.intellij.debugger.engine.events.DebuggerContextCommandImpl;
import com.intellij.debugger.impl.*;
import com.intellij.debugger.jdi.*;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.debugger.settings.NodeRendererSettings;
import com.intellij.debugger.ui.impl.watch.*;
import com.intellij.debugger.ui.tree.ExtraDebugNodesProvider;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.ColoredTextContainer;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.frame.*;
import com.intellij.xdebugger.frame.presentation.XValuePresentation;
import com.intellij.xdebugger.impl.ui.XDebuggerUIConstants;
import com.intellij.xdebugger.settings.XDebuggerSettingsManager;
import com.sun.jdi.*;
import com.sun.jdi.event.ExceptionEvent;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;
import java.util.stream.Collectors;

public class JavaStackFrame extends XStackFrame implements JVMStackFrameInfoProvider {
  private static final Logger LOG = Logger.getInstance(JavaStackFrame.class);
  public static final DummyMessageValueNode LOCAL_VARIABLES_INFO_UNAVAILABLE_MESSAGE_NODE =
    new DummyMessageValueNode(MessageDescriptor.LOCAL_VARIABLES_INFO_UNAVAILABLE.getLabel(), XDebuggerUIConstants.INFORMATION_MESSAGE_ICON);

  private final DebugProcessImpl myDebugProcess;
  @Nullable private final XSourcePosition myXSourcePosition;
  private final NodeManagerImpl myNodeManager;
  @NotNull private final StackFrameDescriptorImpl myDescriptor;
  private JavaDebuggerEvaluator myEvaluator = null;
  private final String myEqualityObject;

  public JavaStackFrame(@NotNull StackFrameDescriptorImpl descriptor, boolean update) {
    myDescriptor = descriptor;
    myEqualityObject = update ? NodeManagerImpl.getContextKeyForFrame(myDescriptor.getFrameProxy()) : null;
    myDebugProcess = ((DebugProcessImpl)descriptor.getDebugProcess());
    myNodeManager = myDebugProcess.getXdebugProcess().getNodeManager();
    myXSourcePosition = DebuggerUtilsEx.toXSourcePosition(myDescriptor.getSourcePosition());
  }

  @NotNull
  public StackFrameDescriptorImpl getDescriptor() {
    return myDescriptor;
  }

  @Nullable
  @Override
  public XDebuggerEvaluator getEvaluator() {
    if (myEvaluator == null) {
      myEvaluator = new JavaDebuggerEvaluator(myDebugProcess, this);
    }
    return myEvaluator;
  }

  @Nullable
  @Override
  public XSourcePosition getSourcePosition() {
    return myXSourcePosition;
  }

  @Override
  public void customizePresentation(@NotNull ColoredTextContainer component) {
    StackFrameDescriptorImpl selectedDescriptor = null;
    DebuggerSession session = myDebugProcess.getSession();
    if (session != null) {
      XDebugSession xSession = session.getXDebugSession();
      if (xSession != null) {
        XStackFrame frame = xSession.getCurrentStackFrame();
        if (frame instanceof JavaStackFrame) {
          selectedDescriptor = ((JavaStackFrame)frame).getDescriptor();
        }
      }
    }
    JavaFramesListRenderer.customizePresentation(myDescriptor, component, selectedDescriptor);
  }

  @Override
  public void computeChildren(@NotNull final XCompositeNode node) {
    if (node.isObsolete()) return;
    ThreadReferenceProxyImpl thread = myDescriptor.getFrameProxy().threadProxy();
    DebuggerContextUtil.scheduleWithCorrectPausedDebuggerContext(myDebugProcess, thread, myDescriptor.getFrameProxy(),
                                                                 c -> scheduleComputeChildrenTask(node, c, thread));
  }

  private void scheduleComputeChildrenTask(@NotNull XCompositeNode node, DebuggerContextImpl context, ThreadReferenceProxyImpl thread) {
    Objects.requireNonNull(context.getManagerThread()).schedule(new DebuggerContextCommandImpl(context, thread) {
      @Override
      public Priority getPriority() {
        return Priority.NORMAL;
      }

      @Override
      public void threadAction(@NotNull SuspendContextImpl suspendContext) {
        if (node.isObsolete()) return;
        XValueChildrenList children = new XValueChildrenList();
        buildVariablesThreadAction(getFrameDebuggerContext(getDebuggerContext()), children, node);
        node.addChildren(children, true);
      }

      @Override
      protected void commandCancelled() {
        if (!node.isObsolete()) {
          node.addChildren(XValueChildrenList.EMPTY, true);
        }
      }
    });
  }

  DebuggerContextImpl getFrameDebuggerContext(@Nullable DebuggerContextImpl context) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    if (context == null) {
      context = myDebugProcess.getDebuggerContext();
    }
    if (context.getFrameProxy() != getStackFrameProxy()) {
      SuspendManager suspendManager = myDebugProcess.getSuspendManager();
      ThreadReferenceProxyImpl thread = getStackFrameProxy().threadProxy();
      SuspendContextImpl pausedSuspendingContext = SuspendManagerUtil.getPausedSuspendingContext(suspendManager, thread);
      SuspendContextImpl threadSuspendContext =
        pausedSuspendingContext != null ? pausedSuspendingContext : SuspendManagerUtil.findContextByThread(suspendManager, thread);

      context = DebuggerContextImpl.createDebuggerContext(
        myDebugProcess.mySession,
        threadSuspendContext,
        thread,
        getStackFrameProxy());
      context.setPositionCache(myDescriptor.getSourcePosition());
      context.initCaches();
    }
    return context;
  }

  @Nullable
  protected XNamedValue createThisNode(EvaluationContextImpl evaluationContext) {
    ObjectReference thisObjectReference = myDescriptor.getThisObject();
    if (thisObjectReference != null) {
      return JavaValue.create(myNodeManager.getThisDescriptor(null, thisObjectReference), evaluationContext, myNodeManager);
    }
    return null;
  }

  protected void addStaticGroup(EvaluationContextImpl evaluationContext, XCompositeNode node) {
    Location location = myDescriptor.getLocation();
    if (location != null && myDescriptor.getThisObject() == null) {
      ReferenceType type = location.declaringType();
      // preload fields
      DebuggerUtilsAsync.allFields(type).thenAccept(__ -> {
        StaticDescriptorImpl staticDescriptor = myNodeManager.getStaticDescriptor(myDescriptor, type);
        if (staticDescriptor.isExpandable()) {
          node.addChildren(
            XValueChildrenList.topGroups(List.of(new JavaStaticGroup(staticDescriptor, evaluationContext, myNodeManager))), false);
        }
      });
    }
  }

  @NotNull
  protected List<? extends XNamedValue> createReturnValueNodes(EvaluationContextImpl evaluationContext) {
    Pair<Method, Value> methodValuePair = myDebugProcess.getLastExecutedMethod();
    if (methodValuePair != null && myDescriptor.getUiIndex() == 0) {
      Value returnValue = methodValuePair.getSecond();
      // try to keep the value as early as possible
      try {
        evaluationContext.keep(returnValue);
      }
      catch (ObjectCollectedException ignored) {
      }
      ValueDescriptorImpl returnValueDescriptor =
        myNodeManager.getMethodReturnValueDescriptor(myDescriptor, methodValuePair.getFirst(), returnValue);
      return Collections.singletonList(JavaValue.create(returnValueDescriptor, evaluationContext, myNodeManager));
    }
    return Collections.emptyList();
  }

  @NotNull
  protected List<? extends XNamedValue> createExceptionNodes(EvaluationContextImpl evaluationContext) {
    if (myDescriptor.getUiIndex() != 0) {
      return Collections.emptyList();
    }
    return StreamEx.of(DebuggerUtilsEx.getEventDescriptors(evaluationContext.getSuspendContext()))
      .map(p -> p.getSecond())
      .select(ExceptionEvent.class)
      .map(ExceptionEvent::exception)
      .nonNull()
      .distinct()
      .map(e -> JavaValue.create(myNodeManager.getThrownExceptionObjectDescriptor(myDescriptor, e), evaluationContext, myNodeManager))
      .toList();
  }

  // copied from DebuggerTree
  protected void buildVariablesThreadAction(DebuggerContextImpl debuggerContext, XValueChildrenList children, XCompositeNode node) {
    try {
      final EvaluationContextImpl evaluationContext = debuggerContext.createEvaluationContext();
      if (evaluationContext == null) {
        return;
      }

      // the message is disabled, see IDEA-281129
      //if (!debuggerContext.isEvaluationPossible()) {
      //  node.setErrorMessage(MessageDescriptor.EVALUATION_NOT_POSSIBLE.getLabel());
      //}

      // this node
      XNamedValue thisNode = createThisNode(evaluationContext);
      if (thisNode != null) {
        children.add(thisNode);
      }

      // static group
      addStaticGroup(evaluationContext, node);

      // last method return value if any
      createReturnValueNodes(evaluationContext).forEach(children::add);

      // context exceptions
      createExceptionNodes(evaluationContext).forEach(children::add);

      try {
        buildVariables(debuggerContext, evaluationContext, myDebugProcess, children, myDescriptor.getThisObject(),
                       myDescriptor.getLocation());
      }
      catch (EvaluateException e) {
        node.setErrorMessage(e.getMessage());
      }
      DebuggerUtilsImpl.forEachSafe(ExtraDebugNodesProvider.getProviders(), p -> p.addExtraNodes(evaluationContext, children));
    }
    catch (InvalidStackFrameException e) {
      LOG.info(e);
    }
    catch (InternalException e) {
      if (e.errorCode() == JvmtiError.INVALID_SLOT) {
        node.setErrorMessage(JavaDebuggerBundle.message("error.corrupt.debug.info", e.getMessage()));
      }
      else {
        throw e;
      }
    }
  }

  private static final Pair<Set<String>, Set<TextWithImports>> EMPTY_USED_VARS =
    Pair.create(Collections.emptySet(), Collections.emptySet());

  // copied from FrameVariablesTree
  private void buildVariables(DebuggerContextImpl debuggerContext,
                              @NotNull final EvaluationContextImpl evaluationContext,
                              @NotNull DebugProcessImpl debugProcess,
                              XValueChildrenList children,
                              ObjectReference thisObjectReference,
                              Location location) throws EvaluateException {
    final Set<String> visibleLocals = new HashSet<>();
    int positionOfLocalVariablesAsFields = children.size();
    final List<FieldDescriptorImpl> outerLocalVariablesAsFields = new SmartList<>();
    if (NodeRendererSettings.getInstance().getClassRenderer().SHOW_VAL_FIELDS_AS_LOCAL_VARIABLES) {
      if (thisObjectReference != null && evaluationContext.getVirtualMachineProxy().canGetSyntheticAttribute()) {
        final ReferenceType thisRefType = thisObjectReference.referenceType();
        if (thisRefType instanceof ClassType && location != null
            && thisRefType.equals(location.declaringType()) && thisRefType.name().contains("$")) { // makes sense for nested classes only
          for (Field field : thisRefType.fields()) {
            if (DebuggerUtils.isSynthetic(field) && StringUtil.startsWith(field.name(), FieldDescriptorImpl.OUTER_LOCAL_VAR_FIELD_PREFIX)) {
              final FieldDescriptorImpl fieldDescriptor = myNodeManager.getFieldDescriptor(myDescriptor, thisObjectReference, field);
              outerLocalVariablesAsFields.add(fieldDescriptor);
              visibleLocals.add(fieldDescriptor.calcValueName());
            }
          }
        }
      }
    }

    boolean myAutoWatchMode = DebuggerSettings.getInstance().AUTO_VARIABLES_MODE;

    try {
      if (!XDebuggerSettingsManager.getInstance().getDataViewSettings().isAutoExpressions() && !myAutoWatchMode) {
        // optimization
        superBuildVariables(evaluationContext, children);
      }
      else {
        final SourcePosition sourcePosition = debuggerContext.getSourcePosition();
        final Map<String, LocalVariableProxyImpl> visibleVariables =
          ContainerUtil.map2Map(getVisibleVariables(),
                                var -> Pair.create(var.name(), var));

        Pair<Set<String>, Set<TextWithImports>> usedVars = EMPTY_USED_VARS;
        if (sourcePosition != null) {
          usedVars = ReadAction.compute(
            () -> DumbService.isDumb(debugProcess.getProject())
                  ? EMPTY_USED_VARS
                  : findReferencedVars(ContainerUtil.union(visibleVariables.keySet(), visibleLocals), sourcePosition));
        }
        // add locals
        if (myAutoWatchMode) {
          List<LocalVariableProxyImpl> localVariables = usedVars.first.stream()
            .map(var -> visibleVariables.get(var))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
          buildLocalVariables(evaluationContext, children, localVariables);
        }
        else {
          superBuildVariables(evaluationContext, children);
        }
        final EvaluationContextImpl evalContextCopy = evaluationContext.withAutoLoadClasses(false);

        if (sourcePosition != null) {
          Set<TextWithImports> extraVars = computeExtraVars(usedVars, sourcePosition, evaluationContext);
          // add extra vars
          addToChildrenFrom(extraVars, children, evaluationContext);
        }

        // add expressions
        addToChildrenFrom(usedVars.second, children, evalContextCopy);
      }
    }
    catch (EvaluateException e) {
      if (e.getCause() instanceof AbsentInformationException) {
        children.add(LOCAL_VARIABLES_INFO_UNAVAILABLE_MESSAGE_NODE);
        // trying to collect values from variable slots
        try {
          for (Map.Entry<DecompiledLocalVariable, Value> entry : LocalVariablesUtil.fetchValues(getStackFrameProxy(), debugProcess, true).entrySet()) {
            children.add(JavaValue.create(myNodeManager.getArgumentValueDescriptor(
              null, entry.getKey(), entry.getValue()), evaluationContext, myNodeManager));
          }
        }
        catch (VMDisconnectedException ex) {
          throw ex;
        }
        catch (Exception ex) {
          LOG.info(ex);
        }
      }
      else {
        throw e;
      }
    }

    if (!outerLocalVariablesAsFields.isEmpty()) {
      // Insert all non-yet added fields before other variables preserving the original order, see IDEA-318062.
      HashSet<String> alreadyAdded = new HashSet<>();
      for (int i = 0; i < children.size(); i++) {
        alreadyAdded.add(children.getName(i));
      }
      for (FieldDescriptorImpl f : outerLocalVariablesAsFields) {
        if (!alreadyAdded.contains(f.calcValueName())) {
          children.add(positionOfLocalVariablesAsFields++, JavaValue.create(f, evaluationContext, myNodeManager));
        }
      }
    }
  }

  protected void buildLocalVariables(final EvaluationContextImpl evaluationContext, XValueChildrenList children, List<LocalVariableProxyImpl> localVariables) {
    for (LocalVariableProxyImpl variable : localVariables) {
      children.add(JavaValue.create(myNodeManager.getLocalVariableDescriptor(null, variable), evaluationContext, myNodeManager));
    }
  }

  private static Set<TextWithImports> computeExtraVars(Pair<Set<String>, Set<TextWithImports>> usedVars,
                                                       @NotNull SourcePosition sourcePosition,
                                                       @NotNull EvaluationContextImpl evalContext) {
    Set<String> alreadyCollected = new HashSet<>(usedVars.first);
    usedVars.second.stream().map(TextWithImports::getText).forEach(alreadyCollected::add);
    Set<TextWithImports> extra = new HashSet<>();
    DebuggerUtilsImpl.forEachSafe(FrameExtraVariablesProvider.EP_NAME,
                                  provider -> {
                                    if (provider.isAvailable(sourcePosition, evalContext)) {
                                      extra.addAll(provider.collectVariables(sourcePosition, evalContext, alreadyCollected));
                                    }
                                  });
    return extra;
  }

  private void addToChildrenFrom(Set<TextWithImports> expressions, XValueChildrenList children, EvaluationContextImpl evaluationContext) {
    for (TextWithImports text : expressions) {
      WatchItemDescriptor descriptor = myNodeManager.getWatchItemDescriptor(null, text, null);
      children.add(JavaValue.create(descriptor, evaluationContext, myNodeManager));
    }
  }

  public static XNamedValue createMessageNode(String text, Icon icon) {
    return new DummyMessageValueNode(text, icon);
  }

  static class DummyMessageValueNode extends XNamedValue {
    private final String myMessage;
    private final Icon myIcon;

    DummyMessageValueNode(String message, Icon icon) {
      super("");
      myMessage = message;
      myIcon = icon;
    }

    @Override
    public void computePresentation(@NotNull XValueNode node, @NotNull XValuePlace place) {
      node.setPresentation(myIcon, new XValuePresentation() {
        @NotNull
        @Override
        public String getSeparator() {
          return "";
        }

        @Override
        public void renderValue(@NotNull XValueTextRenderer renderer) {
          renderer.renderValue(myMessage);
        }
      }, false);
    }

    @Override
    public String toString() {
      return myMessage;
    }
  }

  protected void superBuildVariables(final EvaluationContextImpl evaluationContext, XValueChildrenList children) throws EvaluateException {
    buildLocalVariables(evaluationContext, children, getVisibleVariables());
  }

  @NotNull
  public StackFrameProxyImpl getStackFrameProxy() {
    return myDescriptor.getFrameProxy();
  }

  @Nullable
  @Override
  public Object getEqualityObject() {
    return myEqualityObject;
  }

  @Override
  public String toString() {
    if (myXSourcePosition != null) {
      return "JavaFrame " + myXSourcePosition.getFile().getName() + ":" + myXSourcePosition.getLine();
    }
    else {
      return "JavaFrame position unknown";
    }
  }

  private static class VariablesCollector extends JavaRecursiveElementVisitor {
    private final Set<String> myVisibleLocals;
    private final TextRange myLineRange;
    private final Set<TextWithImports> myExpressions = new HashSet<>();
    private final Set<String> myVars = new HashSet<>();
    private final boolean myCollectExpressions = XDebuggerSettingsManager.getInstance().getDataViewSettings().isAutoExpressions();

    VariablesCollector(Set<String> visibleLocals, TextRange lineRange) {
      myVisibleLocals = visibleLocals;
      myLineRange = lineRange;
    }

    public Set<String> getVars() {
      return myVars;
    }

    public Set<TextWithImports> getExpressions() {
      return myExpressions;
    }

    @Override
    public void visitElement(@NotNull final PsiElement element) {
      if (myLineRange.intersects(element.getTextRange())) {
        super.visitElement(element);
      }
    }

    @Override
    public void visitMethodCallExpression(final @NotNull PsiMethodCallExpression expression) {
      if (myCollectExpressions) {
        final PsiMethod psiMethod = expression.resolveMethod();
        if (psiMethod != null && !DebuggerUtils.hasSideEffectsOrReferencesMissingVars(expression, myVisibleLocals)) {
          myExpressions.add(new TextWithImportsImpl(expression));
        }
      }
      super.visitMethodCallExpression(expression);
    }

    @Override
    public void visitReferenceExpression(final @NotNull PsiReferenceExpression reference) {
      if (myLineRange.intersects(reference.getTextRange()) && reference.resolve() instanceof PsiVariable var) {
        if (var instanceof PsiField) {
          if (myCollectExpressions && !DebuggerUtils.hasSideEffectsOrReferencesMissingVars(reference, myVisibleLocals)) {
            /*
            if (var instanceof PsiEnumConstant && reference.getQualifier() == null) {
              final PsiClass enumClass = ((PsiEnumConstant)var).getContainingClass();
              if (enumClass != null) {
                final PsiExpression expression = JavaPsiFacade.getInstance(var.getProject()).getParserFacade().createExpressionFromText(enumClass.getName() + "." + var.getName(), var);
                final PsiReference ref = expression.getReference();
                if (ref != null) {
                  ref.bindToElement(var);
                  myExpressions.add(new TextWithImportsImpl(expression));
                }
              }
            }
            else {
              myExpressions.add(new TextWithImportsImpl(reference));
            }
            */
            final PsiModifierList modifierList = var.getModifierList();
            boolean isConstant = (var instanceof PsiEnumConstant) ||
                                 (modifierList != null && 
                                  modifierList.hasModifierProperty(PsiModifier.STATIC) && modifierList.hasModifierProperty(PsiModifier.FINAL));
            if (!isConstant) {
              myExpressions.add(new TextWithImportsImpl(reference));
            }
          }
        }
        else {
          if (myVisibleLocals.contains(var.getName())) {
            myVars.add(var.getName());
          }
          else {
            // fix for variables used in inner classes
            if (!Comparing.equal(PsiTreeUtil.getParentOfType(reference, PsiClass.class),
                                 PsiTreeUtil.getParentOfType(var, PsiClass.class))) {
              myExpressions.add(new TextWithImportsImpl(reference));
            }
          }
        }
      }
      super.visitReferenceExpression(reference);
    }

    @Override
    public void visitArrayAccessExpression(final @NotNull PsiArrayAccessExpression expression) {
      if (myCollectExpressions && !DebuggerUtils.hasSideEffectsOrReferencesMissingVars(expression, myVisibleLocals)) {
        myExpressions.add(new TextWithImportsImpl(expression));
      }
      super.visitArrayAccessExpression(expression);
    }

    @Override
    public void visitParameter(final @NotNull PsiParameter parameter) {
      processVariable(parameter);
      super.visitParameter(parameter);
    }

    @Override
    public void visitLocalVariable(final @NotNull PsiLocalVariable variable) {
      processVariable(variable);
      super.visitLocalVariable(variable);
    }

    private void processVariable(final PsiVariable variable) {
      if (myLineRange.intersects(variable.getTextRange()) && myVisibleLocals.contains(variable.getName())) {
        myVars.add(variable.getName());
      }
    }

    @Override
    public void visitClass(final @NotNull PsiClass aClass) {
      // Do not step in to local and anonymous classes...
    }
  }

  protected List<LocalVariableProxyImpl> getVisibleVariables() throws EvaluateException {
    return getStackFrameProxy().visibleVariables();
  }

  private static boolean shouldSkipLine(final PsiFile file, Document doc, int line) {
    final int start = doc.getLineStartOffset(line);
    final int end = doc.getLineEndOffset(line);
    final int _start = CharArrayUtil.shiftForward(doc.getCharsSequence(), start, " \n\t");
    if (_start >= end) {
      return true;
    }

    TextRange alreadyChecked = null;
    for (PsiElement elem = file.findElementAt(_start); elem != null && elem.getTextOffset() <= end && (alreadyChecked == null || !alreadyChecked.contains(elem.getTextRange())); elem = elem.getNextSibling()) {
      for (PsiElement _elem = elem; _elem.getTextOffset() >= _start; _elem = _elem.getParent()) {
        alreadyChecked = _elem.getTextRange();

        if (_elem instanceof PsiDeclarationStatement) {
          final PsiElement[] declared = ((PsiDeclarationStatement)_elem).getDeclaredElements();
          for (PsiElement declaredElement : declared) {
            if (declaredElement instanceof PsiVariable) {
              return false;
            }
          }
        }

        if (_elem instanceof PsiJavaCodeReferenceElement) {
          try {
            final PsiElement resolved = ((PsiJavaCodeReferenceElement)_elem).resolve();
            if (resolved instanceof PsiVariable) {
              return false;
            }
          }
          catch (IndexNotReadyException e) {
            return false;
          }
        }
      }
    }
    return true;
  }

  private static Pair<Set<String>, Set<TextWithImports>> findReferencedVars(Set<String> visibleVars, @NotNull SourcePosition position) {
    final int line = position.getLine();
    if (line < 0) {
      return Pair.create(Collections.emptySet(), Collections.emptySet());
    }
    final PsiFile positionFile = position.getFile();
    if (!positionFile.isValid() || !positionFile.getLanguage().isKindOf(JavaLanguage.INSTANCE)) {
      return Pair.create(visibleVars, Collections.emptySet());
    }

    final VirtualFile vFile = positionFile.getVirtualFile();
    final Document doc = vFile != null ? FileDocumentManager.getInstance().getDocument(vFile) : null;
    if (doc == null || doc.getLineCount() == 0 || line > (doc.getLineCount() - 1)) {
      return Pair.create(Collections.emptySet(), Collections.emptySet());
    }

    final TextRange limit = calculateLimitRange(positionFile, doc, line);

    int startLine = Math.max(limit.getStartOffset(), line - 1);
    startLine = Math.min(startLine, limit.getEndOffset());
    while (startLine > limit.getStartOffset() && shouldSkipLine(positionFile, doc, startLine)) {
      startLine--;
    }
    final int startOffset = doc.getLineStartOffset(startLine);

    int endLine = Math.min(line + 2, limit.getEndOffset());
    while (endLine < limit.getEndOffset() && shouldSkipLine(positionFile, doc, endLine)) {
      endLine++;
    }
    final int endOffset = doc.getLineEndOffset(endLine);

    final TextRange lineRange = new TextRange(startOffset, endOffset);
    if (!lineRange.isEmpty()) {
      final int offset = CharArrayUtil.shiftForward(doc.getCharsSequence(), doc.getLineStartOffset(line), " \t");
      PsiElement element = positionFile.findElementAt(offset);
      if (element != null) {
        PsiMethod method = PsiTreeUtil.getNonStrictParentOfType(element, PsiMethod.class);
        if (method != null) {
          element = method;
        }
        else {
          PsiField field = PsiTreeUtil.getNonStrictParentOfType(element, PsiField.class);
          if (field != null) {
            element = field;
          }
          else {
            final PsiClassInitializer initializer = PsiTreeUtil.getNonStrictParentOfType(element, PsiClassInitializer.class);
            if (initializer != null) {
              element = initializer;
            }
          }
        }

        if (element instanceof PsiCompiledElement) {
          return Pair.create(visibleVars, Collections.emptySet());
        }
        else {
          VariablesCollector collector = new VariablesCollector(visibleVars, adjustRange(element, lineRange));
          element.accept(collector);
          return Pair.create(collector.getVars(), collector.getExpressions());
        }
      }
    }
    return Pair.create(Collections.emptySet(), Collections.emptySet());
  }

  private static TextRange calculateLimitRange(final PsiFile file, final Document doc, final int line) {
    final int offset = doc.getLineStartOffset(line);
    if (offset > 0) {
      PsiMethod method = PsiTreeUtil.getParentOfType(file.findElementAt(offset), PsiMethod.class, false);
      if (method != null) {
        final TextRange elemRange = method.getTextRange();
        return new TextRange(doc.getLineNumber(elemRange.getStartOffset()), doc.getLineNumber(elemRange.getEndOffset()));
      }
    }
    return new TextRange(0, doc.getLineCount() - 1);
  }

  private static TextRange adjustRange(final PsiElement element, final TextRange originalRange) {
    final Ref<TextRange> rangeRef = new Ref<>(originalRange);
    element.accept(new JavaRecursiveElementVisitor() {
      @Override
      public void visitExpressionStatement(final @NotNull PsiExpressionStatement statement) {
        final TextRange stRange = statement.getTextRange();
        if (originalRange.intersects(stRange)) {
          final TextRange currentRange = rangeRef.get();
          final int start = Math.min(currentRange.getStartOffset(), stRange.getStartOffset());
          final int end = Math.max(currentRange.getEndOffset(), stRange.getEndOffset());
          rangeRef.set(new TextRange(start, end));
        }
      }
    });
    return rangeRef.get();
  }

  @Override
  public boolean isSynthetic() {
    return myDescriptor.isSynthetic();
  }

  @Override
  public boolean isInLibraryContent() {
    return myDescriptor.isInLibraryContent();
  }

  @Override
  public boolean shouldHide() {
    return myDescriptor.shouldHide();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    JavaStackFrame frame = (JavaStackFrame)o;

    if (!myDescriptor.getFrameProxy().equals(frame.myDescriptor.getFrameProxy())) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myDescriptor.getFrameProxy().hashCode();
  }
}
