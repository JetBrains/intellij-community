/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.debugger.engine;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.evaluation.TextWithImports;
import com.intellij.debugger.engine.evaluation.TextWithImportsImpl;
import com.intellij.debugger.engine.events.DebuggerContextCommandImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.jdi.DecompiledLocalVariable;
import com.intellij.debugger.jdi.LocalVariableProxyImpl;
import com.intellij.debugger.jdi.LocalVariablesUtil;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.memory.utils.StackFrameItem;
import com.intellij.debugger.settings.CapturePoint;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.debugger.settings.NodeRendererSettings;
import com.intellij.debugger.ui.breakpoints.Breakpoint;
import com.intellij.debugger.ui.impl.watch.*;
import com.intellij.debugger.ui.tree.render.DescriptorLabelListener;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
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
import com.intellij.ui.SimpleTextAttributes;
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
import com.sun.jdi.event.Event;
import com.sun.jdi.event.ExceptionEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

/**
 * @author egor
 */
public class JavaStackFrame extends XStackFrame implements JVMStackFrameInfoProvider {
  private static final Logger LOG = Logger.getInstance(JavaStackFrame.class);
  public static final DummyMessageValueNode LOCAL_VARIABLES_INFO_UNAVAILABLE_MESSAGE_NODE =
    new DummyMessageValueNode(MessageDescriptor.LOCAL_VARIABLES_INFO_UNAVAILABLE.getLabel(), XDebuggerUIConstants.INFORMATION_MESSAGE_ICON);

  private final DebugProcessImpl myDebugProcess;
  @Nullable private final XSourcePosition myXSourcePosition;
  private final NodeManagerImpl myNodeManager;
  @NotNull private final StackFrameDescriptorImpl myDescriptor;
  private static final JavaFramesListRenderer FRAME_RENDERER = new JavaFramesListRenderer();
  private JavaDebuggerEvaluator myEvaluator = null;
  private final String myEqualityObject;
  private CapturePoint myInsertCapturePoint;

  public JavaStackFrame(@NotNull StackFrameDescriptorImpl descriptor, boolean update) {
    myDescriptor = descriptor;
    if (update) {
      myDescriptor.setContext(null);
      myDescriptor.updateRepresentation(null, DescriptorLabelListener.DUMMY_LISTENER);
    }
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
    FRAME_RENDERER.customizePresentation(myDescriptor, component, selectedDescriptor);
    if (myInsertCapturePoint != null) {
      component.setIcon(XDebuggerUIConstants.INFORMATION_MESSAGE_ICON);
    }
  }

  @Override
  public void computeChildren(@NotNull final XCompositeNode node) {
    if (node.isObsolete()) return;
    myDebugProcess.getManagerThread().schedule(new DebuggerContextCommandImpl(myDebugProcess.getDebuggerContext(), myDescriptor.getFrameProxy().threadProxy()) {
      @Override
      public Priority getPriority() {
        return Priority.NORMAL;
      }

      @Override
      public void threadAction(@NotNull SuspendContextImpl suspendContext) {
        if (node.isObsolete()) return;
        if (myInsertCapturePoint != null) {
          node.setMessage("Async stacktrace from " +
                          myInsertCapturePoint.myClassName + "." + myInsertCapturePoint.myMethodName +
                          " could be available here, enable in", XDebuggerUIConstants.INFORMATION_MESSAGE_ICON,
                          SimpleTextAttributes.REGULAR_ATTRIBUTES, StackFrameItem.CAPTURE_SETTINGS_OPENER);
        }
        XValueChildrenList children = new XValueChildrenList();
        buildVariablesThreadAction(getFrameDebuggerContext(getDebuggerContext()), children, node);
        node.addChildren(children, true);
      }
    });
  }

  DebuggerContextImpl getFrameDebuggerContext(@Nullable DebuggerContextImpl context) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    if (context == null) {
      context = myDebugProcess.getDebuggerContext();
    }
    if (context.getFrameProxy() != getStackFrameProxy()) {
      SuspendContextImpl threadSuspendContext =
        SuspendManagerUtil.findContextByThread(myDebugProcess.getSuspendManager(), getStackFrameProxy().threadProxy());
      context = DebuggerContextImpl.createDebuggerContext(
        myDebugProcess.mySession,
        threadSuspendContext,
        getStackFrameProxy().threadProxy(),
        getStackFrameProxy());
      context.setPositionCache(myDescriptor.getSourcePosition());
      context.initCaches();
    }
    return context;
  }

  // copied from DebuggerTree
  private void buildVariablesThreadAction(DebuggerContextImpl debuggerContext, XValueChildrenList children, XCompositeNode node) {
    try {
      final EvaluationContextImpl evaluationContext = debuggerContext.createEvaluationContext();
      if (evaluationContext == null) {
        return;
      }
      if (!debuggerContext.isEvaluationPossible()) {
        node.setErrorMessage(MessageDescriptor.EVALUATION_NOT_POSSIBLE.getLabel());
        //myChildren.add(myNodeManager.createNode(MessageDescriptor.EVALUATION_NOT_POSSIBLE, evaluationContext));
      }

      final Location location = myDescriptor.getLocation();

      final ObjectReference thisObjectReference = myDescriptor.getThisObject();
      if (thisObjectReference != null) {
        ValueDescriptorImpl thisDescriptor = myNodeManager.getThisDescriptor(null, thisObjectReference);
        children.add(JavaValue.create(thisDescriptor, evaluationContext, myNodeManager));
      }
      else if (location != null) {
        StaticDescriptorImpl staticDecriptor = myNodeManager.getStaticDescriptor(myDescriptor, location.declaringType());
        if (staticDecriptor.isExpandable()) {
          children.addTopGroup(new JavaStaticGroup(staticDecriptor, evaluationContext, myNodeManager));
        }
      }

      DebugProcessImpl debugProcess = debuggerContext.getDebugProcess();
      if (debugProcess == null) {
        return;
      }

      // add last method return value if any
      final Pair<Method, Value> methodValuePair = debugProcess.getLastExecutedMethod();
      if (methodValuePair != null && myDescriptor.getUiIndex() == 0) {
        ValueDescriptorImpl returnValueDescriptor = myNodeManager.getMethodReturnValueDescriptor(myDescriptor, methodValuePair.getFirst(), methodValuePair.getSecond());
        children.add(JavaValue.create(returnValueDescriptor, evaluationContext, myNodeManager));
      }
      // add context exceptions
      Set<ObjectReference> exceptions = new HashSet<>();
      for (Pair<Breakpoint, Event> pair : DebuggerUtilsEx.getEventDescriptors(debuggerContext.getSuspendContext())) {
        Event debugEvent = pair.getSecond();
        if (debugEvent instanceof ExceptionEvent) {
          ObjectReference exception = ((ExceptionEvent)debugEvent).exception();
          if (exception != null) {
            exceptions.add(exception);
          }
        }
      }
      exceptions.forEach(e -> children.add(
        JavaValue.create(myNodeManager.getThrownExceptionObjectDescriptor(myDescriptor, e), evaluationContext, myNodeManager)));

      try {
        buildVariables(debuggerContext, evaluationContext, debugProcess, children, thisObjectReference, location);
        //if (classRenderer.SORT_ASCENDING) {
        //  Collections.sort(myChildren, NodeManagerImpl.getNodeComparator());
        //}
      }
      catch (EvaluateException e) {
        node.setErrorMessage(e.getMessage());
        //myChildren.add(myNodeManager.createMessageNode(new MessageDescriptor(e.getMessage())));
      }
    }
    catch (InvalidStackFrameException e) {
      LOG.info(e);
      //myChildren.clear();
      //notifyCancelled();
    }
    catch (InternalException e) {
      if (e.errorCode() == 35) {
        node.setErrorMessage(DebuggerBundle.message("error.corrupt.debug.info", e.getMessage()));
        //myChildren.add(
        //  myNodeManager.createMessageNode(new MessageDescriptor(DebuggerBundle.message("error.corrupt.debug.info", e.getMessage()))));
      }
      else {
        throw e;
      }
    }
  }

  private static final Pair<Set<String>, Set<TextWithImports>> EMPTY_USED_VARS =
    Pair.create(Collections.emptySet(), Collections.<TextWithImports>emptySet());

  // copied from FrameVariablesTree
  private void buildVariables(DebuggerContextImpl debuggerContext,
                              final EvaluationContextImpl evaluationContext,
                              @NotNull DebugProcessImpl debugProcess,
                              XValueChildrenList children,
                              ObjectReference thisObjectReference,
                              Location location) throws EvaluateException {
    final Set<String> visibleLocals = new HashSet<>();
    if (NodeRendererSettings.getInstance().getClassRenderer().SHOW_VAL_FIELDS_AS_LOCAL_VARIABLES) {
      if (thisObjectReference != null && debugProcess.getVirtualMachineProxy().canGetSyntheticAttribute()) {
        final ReferenceType thisRefType = thisObjectReference.referenceType();
        if (thisRefType instanceof ClassType && location != null
            && thisRefType.equals(location.declaringType()) && thisRefType.name().contains("$")) { // makes sense for nested classes only
          for (Field field : thisRefType.fields()) {
            if (DebuggerUtils.isSynthetic(field) && StringUtil.startsWith(field.name(), FieldDescriptorImpl.OUTER_LOCAL_VAR_FIELD_PREFIX)) {
              final FieldDescriptorImpl fieldDescriptor = myNodeManager.getFieldDescriptor(myDescriptor, thisObjectReference, field);
              children.add(JavaValue.create(fieldDescriptor, evaluationContext, myNodeManager));
              visibleLocals.add(fieldDescriptor.calcValueName());
            }
          }
        }
      }
    }

    boolean myAutoWatchMode = DebuggerSettings.getInstance().AUTO_VARIABLES_MODE;
    if (evaluationContext == null) {
      return;
    }

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
          usedVars = ReadAction.compute(() -> findReferencedVars(ContainerUtil.union(visibleVariables.keySet(), visibleLocals), sourcePosition));
        }
          // add locals
        if (myAutoWatchMode) {
          for (String var : usedVars.first) {
            LocalVariableProxyImpl local = visibleVariables.get(var);
            if (local != null) {
              children.add(JavaValue.create(myNodeManager.getLocalVariableDescriptor(null, local), evaluationContext, myNodeManager));
            }
          }
        }
        else {
          superBuildVariables(evaluationContext, children);
        }
        final EvaluationContextImpl evalContextCopy = evaluationContext.createEvaluationContext(evaluationContext.computeThisObject());
        evalContextCopy.setAutoLoadClasses(false);

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
        catch (Exception ex) {
          LOG.info(ex);
        }
      }
      else {
        throw e;
      }
    }
  }

  private static Set<TextWithImports> computeExtraVars(Pair<Set<String>, Set<TextWithImports>> usedVars,
                                                       @NotNull SourcePosition sourcePosition,
                                                       @NotNull EvaluationContextImpl evalContext) {
    Set<String> alreadyCollected = new HashSet<>(usedVars.first);
    usedVars.second.stream().map(TextWithImports::getText).forEach(alreadyCollected::add);
    Set<TextWithImports> extra = new HashSet<>();
    for (FrameExtraVariablesProvider provider : FrameExtraVariablesProvider.EP_NAME.getExtensions()) {
      if (provider.isAvailable(sourcePosition, evalContext)) {
        extra.addAll(provider.collectVariables(sourcePosition, evalContext, alreadyCollected));
      }
    }
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

    public DummyMessageValueNode(String message, Icon icon) {
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
    for (LocalVariableProxyImpl local : getVisibleVariables()) {
      children.add(JavaValue.create(myNodeManager.getLocalVariableDescriptor(null, local), evaluationContext, myNodeManager));
    }
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

    public VariablesCollector(Set<String> visibleLocals, TextRange lineRange) {
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
    public void visitElement(final PsiElement element) {
      if (myLineRange.intersects(element.getTextRange())) {
        super.visitElement(element);
      }
    }

    @Override
    public void visitMethodCallExpression(final PsiMethodCallExpression expression) {
      if (myCollectExpressions) {
        final PsiMethod psiMethod = expression.resolveMethod();
        if (psiMethod != null && !DebuggerUtils.hasSideEffectsOrReferencesMissingVars(expression, myVisibleLocals)) {
          myExpressions.add(new TextWithImportsImpl(expression));
        }
      }
      super.visitMethodCallExpression(expression);
    }

    @Override
    public void visitReferenceExpression(final PsiReferenceExpression reference) {
      if (myLineRange.intersects(reference.getTextRange())) {
        final PsiElement psiElement = reference.resolve();
        if (psiElement instanceof PsiVariable) {
          final PsiVariable var = (PsiVariable)psiElement;
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
                                   (modifierList != null && modifierList.hasModifierProperty(PsiModifier.STATIC) && modifierList.hasModifierProperty(PsiModifier.FINAL));
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
      }
      super.visitReferenceExpression(reference);
    }

    @Override
    public void visitArrayAccessExpression(final PsiArrayAccessExpression expression) {
      if (myCollectExpressions && !DebuggerUtils.hasSideEffectsOrReferencesMissingVars(expression, myVisibleLocals)) {
        myExpressions.add(new TextWithImportsImpl(expression));
      }
      super.visitArrayAccessExpression(expression);
    }

    @Override
    public void visitParameter(final PsiParameter parameter) {
      processVariable(parameter);
      super.visitParameter(parameter);
    }

    @Override
    public void visitLocalVariable(final PsiLocalVariable variable) {
      processVariable(variable);
      super.visitLocalVariable(variable);
    }

    private void processVariable(final PsiVariable variable) {
      if (myLineRange.intersects(variable.getTextRange()) && myVisibleLocals.contains(variable.getName())) {
        myVars.add(variable.getName());
      }
    }

    @Override
    public void visitClass(final PsiClass aClass) {
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
    for (PsiElement elem = file.findElementAt(_start); elem != null && elem.getTextOffset() <= end && (alreadyChecked == null || !alreadyChecked .contains(elem.getTextRange())); elem = elem.getNextSibling()) {
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
      return Pair.create(Collections.emptySet(), Collections.<TextWithImports>emptySet());
    }
    final PsiFile positionFile = position.getFile();
    if (!positionFile.isValid() || !positionFile.getLanguage().isKindOf(JavaLanguage.INSTANCE)) {
      return Pair.create(visibleVars, Collections.emptySet());
    }

    final VirtualFile vFile = positionFile.getVirtualFile();
    final Document doc = vFile != null ? FileDocumentManager.getInstance().getDocument(vFile) : null;
    if (doc == null || doc.getLineCount() == 0 || line > (doc.getLineCount() - 1)) {
      return Pair.create(Collections.emptySet(), Collections.<TextWithImports>emptySet());
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

        //noinspection unchecked
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
    return Pair.create(Collections.emptySet(), Collections.<TextWithImports>emptySet());
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
      @Override public void visitExpressionStatement(final PsiExpressionStatement statement) {
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

  public void setInsertCapturePoint(CapturePoint insertCapturePoint) {
    myInsertCapturePoint = insertCapturePoint;
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
