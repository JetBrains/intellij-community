// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.ui.impl.watch;

import com.intellij.Patches;
import com.intellij.debugger.DebuggerContext;
import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.engine.*;
import com.intellij.debugger.engine.evaluation.CodeFragmentFactoryContextWrapper;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.impl.*;
import com.intellij.debugger.jdi.VirtualMachineProxyImpl;
import com.intellij.debugger.memory.utils.NamesUtils;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.debugger.settings.NodeRendererSettings;
import com.intellij.debugger.ui.overhead.OverheadTimings;
import com.intellij.debugger.ui.tree.DebuggerTreeNode;
import com.intellij.debugger.ui.tree.NodeDescriptor;
import com.intellij.debugger.ui.tree.NodeDescriptorNameAdjuster;
import com.intellij.debugger.ui.tree.ValueDescriptor;
import com.intellij.debugger.ui.tree.render.Renderer;
import com.intellij.debugger.ui.tree.render.*;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.ui.JBColor;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.xdebugger.frame.XValueModifier;
import com.intellij.xdebugger.frame.XValueNode;
import com.intellij.xdebugger.frame.presentation.XRegularValuePresentation;
import com.intellij.xdebugger.impl.frame.XValueMarkers;
import com.intellij.xdebugger.impl.ui.tree.ValueMarkup;
import com.sun.jdi.*;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import javax.swing.*;
import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.RejectedExecutionException;

public abstract class ValueDescriptorImpl extends NodeDescriptorImpl implements ValueDescriptor {
  protected final Project myProject;
  private final CompletableFuture<Void> myInitFuture;

  NodeRenderer myRenderer = null;

  NodeRenderer myAutoRenderer = null;

  private Value myValue;

  private EvaluateException myValueException;
  protected EvaluationContextImpl myStoredEvaluationContext = null;

  private String myIdLabel;
  private String myValueText;
  private String myCompactValueText;
  private boolean myFullValue = false;

  @Nullable
  private Icon myValueIcon;

  protected boolean myIsNew = true;
  private boolean myIsDirty = false;
  private boolean myIsLvalue = false;
  private boolean myIsExpandable;

  private boolean myShowIdLabel = true;

  private static final OnDemandPresentationProvider ourDefaultOnDemandPresentationProvider = node -> {
    node.setFullValueEvaluator(OnDemandRenderer.createFullValueEvaluator(JavaDebuggerBundle.message("message.node.evaluate")));
    node.setPresentation(AllIcons.Debugger.Db_watch, new XRegularValuePresentation("", null, ""), false);
  };

  private OnDemandPresentationProvider myOnDemandPresentationProvider = ourDefaultOnDemandPresentationProvider;

  protected ValueDescriptorImpl(Project project, Value value) {
    myProject = project;
    myValue = value;
    myInitFuture = CompletableFuture.completedFuture(null);
  }

  protected ValueDescriptorImpl(Project project) {
    myProject = project;
    myInitFuture = new CompletableFuture<>();
  }

  private void assertValueReady() {
    if (!isValueReady()) {
      LOG.error("Value is not yet calculated for " + getClass());
    }
  }

  @Override
  public boolean isArray() {
    assertValueReady();
    return myValue instanceof ArrayReference;
  }


  public boolean isDirty() {
    assertValueReady();
    return myIsDirty;
  }

  @Override
  public boolean isLvalue() {
    assertValueReady();
    return myIsLvalue;
  }

  @Override
  public boolean isNull() {
    assertValueReady();
    return myValue == null;
  }

  @Override
  public boolean isString() {
    assertValueReady();
    return myValue instanceof StringReference;
  }

  @Override
  public boolean isPrimitive() {
    assertValueReady();
    return myValue instanceof PrimitiveValue;
  }

  public boolean isEnumConstant() {
    assertValueReady();
    return myValue instanceof ObjectReference && isEnumConstant(((ObjectReference)myValue));
  }

  public boolean isValueValid() {
    return myValueException == null;
  }

  public boolean isShowIdLabel() {
    return myShowIdLabel && DebuggerSettings.getInstance().SHOW_TYPES;
  }

  public void setShowIdLabel(boolean showIdLabel) {
    myShowIdLabel = showIdLabel;
  }

  public boolean isValueReady() {
    return myInitFuture.isDone();
  }

  @Override
  public Value getValue() {
    // the following code makes sense only if we do not use ObjectReference.enableCollection() / disableCollection()
    // to keep temporary objects
    if (Patches.IBM_JDK_DISABLE_COLLECTION_BUG) {
      final EvaluationContextImpl evalContext = myStoredEvaluationContext;
      if (evalContext != null && !evalContext.getSuspendContext().isResumed() &&
          myValue instanceof ObjectReference && VirtualMachineProxyImpl.isCollected((ObjectReference)myValue)) {

        final Semaphore semaphore = new Semaphore();
        semaphore.down();
        evalContext.getManagerThread().invoke(new SuspendContextCommandImpl(evalContext.getSuspendContext()) {
          @Override
          public void contextAction(@NotNull SuspendContextImpl suspendContext) {
            // re-setting the context will cause value recalculation
            try {
              setContext(myStoredEvaluationContext);
            }
            finally {
              semaphore.up();
            }
          }

          @Override
          protected void commandCancelled() {
            semaphore.up();
          }
        });
        semaphore.waitFor();
      }
    }

    assertValueReady();
    return myValue;
  }

  @Override
  public boolean isExpandable() {
    return myIsExpandable;
  }

  public abstract Value calcValue(EvaluationContextImpl evaluationContext) throws EvaluateException;

  @Override
  public final void setContext(EvaluationContextImpl evaluationContext) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    myStoredEvaluationContext = evaluationContext;
    Value value;
    try {
      value = calcValue(evaluationContext);

      if (!myIsNew) {
        try {
          if (myValue instanceof DoubleValue && Double.isNaN(((DoubleValue)myValue).doubleValue())) {
            myIsDirty = !(value instanceof DoubleValue);
          }
          else if (myValue instanceof FloatValue && Float.isNaN(((FloatValue)myValue).floatValue())) {
            myIsDirty = !(value instanceof FloatValue);
          }
          else {
            myIsDirty = !Objects.equals(value, myValue);
          }
        }
        catch (ObjectCollectedException ignored) {
          myIsDirty = true;
        }
      }
      myValue = value;
      myValueException = null;
    }
    catch (EvaluateException e) {
      myValueException = e;
      setFailed(e);
      myValue = getTargetExceptionWithStackTraceFilled(evaluationContext, e,
                                                       isPrintExceptionToConsole() || ApplicationManager.getApplication().isUnitTestMode());
      myIsExpandable = false;
    }
    finally {
      myInitFuture.complete(null);
    }

    myIsNew = false;
  }

  protected boolean isPrintExceptionToConsole() {
    return true;
  }

  public void applyOnDemandPresentation(@NotNull XValueNode node) {
    myOnDemandPresentationProvider.setPresentation(node);
  }

  public void setOnDemandPresentationProvider(@NotNull OnDemandPresentationProvider onDemandPresentationProvider) {
    myOnDemandPresentationProvider = onDemandPresentationProvider;
  }

  @ApiStatus.Internal
  public CompletableFuture<Void> getInitFuture() {
    return myInitFuture;
  }

  @Nullable
  protected static Value invokeExceptionGetStackTrace(ObjectReference exceptionObj, EvaluationContextImpl evaluationContext)
    throws EvaluateException {
    Method method = DebuggerUtils.findMethod(exceptionObj.referenceType(), "getStackTrace", "()[Ljava/lang/StackTraceElement;");
    if (method != null) {
      return evaluationContext.getDebugProcess().invokeInstanceMethod(
        evaluationContext, exceptionObj, method, Collections.emptyList(), 0, true);
    }
    return null;
  }

  @Nullable
  private static ObjectReference getTargetExceptionWithStackTraceFilled(@Nullable EvaluationContextImpl evaluationContext,
                                                                        EvaluateException ex,
                                                                        boolean printToConsole) {
    final ObjectReference exceptionObj = ex.getExceptionFromTargetVM();
    if (exceptionObj != null && evaluationContext != null) {
      try {
        Value trace = invokeExceptionGetStackTrace(exceptionObj, evaluationContext);

        // print to console as well
        if (printToConsole && trace instanceof ArrayReference) {
          evaluationContext.getDebugProcess().printToConsole(DebuggerUtilsImpl.getExceptionText(evaluationContext, exceptionObj));
        }
      }
      catch (EvaluateException ignored) {
      }
      catch (Throwable e) {
        LOG.info(e); // catch all exceptions to ensure the method returns gracefully
      }
    }
    return exceptionObj;
  }

  @Override
  public void setAncestor(NodeDescriptor oldDescriptor) {
    super.setAncestor(oldDescriptor);
    myIsNew = false;
    if (!isValueReady()) {
      ValueDescriptorImpl other = (ValueDescriptorImpl)oldDescriptor;
      if (other.isValueReady()) {
        myValue = other.getValue();
        myInitFuture.complete(null);
      }
    }
  }

  protected void setLvalue(boolean value) {
    myIsLvalue = value;
  }

  @Override
  protected String calcRepresentation(EvaluationContextImpl context, DescriptorLabelListener labelListener) {
    DebuggerManagerThreadImpl.assertIsManagerThread();

    DebugProcessImpl debugProcess = context.getDebugProcess();
    getRenderer(debugProcess)
      .thenAccept(renderer -> calcRepresentation(context, labelListener, debugProcess, renderer))
      .exceptionally(throwable -> {
        throwable = DebuggerUtilsAsync.unwrap(throwable);
        if (throwable instanceof EvaluateException) {
          setValueLabelFailed((EvaluateException)throwable);
        }
        else {
          String message;
          if (throwable instanceof CancellationException) {
            message = JavaDebuggerBundle.message("error.context.has.changed");
          }
          else if (throwable instanceof VMDisconnectedException || throwable instanceof RejectedExecutionException) {
            message = JavaDebuggerBundle.message("error.vm.disconnected");
          }
          else {
            message = JavaDebuggerBundle.message("internal.debugger.error");
            LOG.error(new Throwable(throwable));
          }
          setValueLabelFailed(new EvaluateException(message));
        }
        labelListener.labelChanged();
        return null;
      });

    return "";
  }

  @NotNull
  private String calcRepresentation(EvaluationContextImpl context,
                                    DescriptorLabelListener labelListener,
                                    DebugProcessImpl debugProcess,
                                    NodeRenderer renderer) {
    DebuggerManagerThreadImpl.assertIsManagerThread();

    EvaluateException valueException = myValueException;
    CompletableFuture<Boolean> expandableFuture;
    if (valueException == null || valueException.getExceptionFromTargetVM() != null) {
      expandableFuture = getChildrenRenderer(debugProcess)
        .thenCompose(r -> r.isExpandableAsync(getValue(), context, this));
    }
    else {
      expandableFuture = CompletableFuture.completedFuture(false);
    }

    if (!OnDemandRenderer.isOnDemandForced(debugProcess)) {
      try {
        setValueIcon(renderer.calcValueIcon(this, context, labelListener));
      }
      catch (EvaluateException e) {
        LOG.info(e);
        setValueIcon(null);
      }
    }

    //set label id
    if (isShowIdLabel() && renderer instanceof NodeRendererImpl) {
      setIdLabel(((NodeRendererImpl)renderer).calcIdLabel(this, debugProcess, labelListener));
    }

    if (valueException == null) {
      long start = renderer instanceof NodeRendererImpl && ((NodeRendererImpl)renderer).hasOverhead() ? System.currentTimeMillis() : 0;
      try {
        setValueLabel(renderer.calcLabel(this, context, labelListener));
      }
      catch (EvaluateException e) {
        setValueLabelFailed(e);
      }
      finally {
        if (start > 0) {
          OverheadTimings.add(debugProcess, new NodeRendererImpl.Overhead((NodeRendererImpl)renderer), 1, System.currentTimeMillis() - start);
        }
      }
    }
    else {
      setValueLabelFailed(valueException);
    }

    // only call labelChanged when we have expandable value
    expandableFuture.whenComplete((res, ex) -> {
      if (ex == null) {
        myIsExpandable = res;
      }
      else {
        ex = DebuggerUtilsAsync.unwrap(ex);
        if (ex instanceof EvaluateException) {
          LOG.warn(new Throwable(ex));
        }
        else if (!(ex instanceof CancellationException) && !(ex instanceof VMDisconnectedException)) {
          LOG.error(new Throwable(ex));
        }
      }
      labelListener.labelChanged();
    });

    return ""; // we have overridden getLabel
  }

  @Override
  public String getLabel() {
    @NlsSafe String label = calcValueName() + getDeclaredTypeLabel() + " = " + getValueLabel();
    return label;
  }

  public ValueDescriptorImpl getFullValueDescriptor() {
    ValueDescriptorImpl descriptor = new ValueDescriptorImpl(myProject, myValue) {
      @Override
      public Value calcValue(EvaluationContextImpl evaluationContext) throws EvaluateException {
        return myValue;
      }

      @Override
      public PsiExpression getDescriptorEvaluation(DebuggerContext context) throws EvaluateException {
        return null;
      }

      @Override
      public CompletableFuture<NodeRenderer> getRenderer(DebugProcessImpl debugProcess) {
        return ValueDescriptorImpl.this.getRenderer(debugProcess);
      }

      @Override
      public <T> T getUserData(@NotNull Key<T> key) {
        return ValueDescriptorImpl.this.getUserData(key);
      }
    };
    descriptor.myFullValue = true;
    return descriptor;
  }

  @Override
  public void setValueLabel(@NotNull String label) {
    myValueText = myFullValue ? label : DebuggerUtilsEx.truncateString(label);
  }

  public void setCompactValueLabel(String label) {
    myCompactValueText = label;
  }

  @Nullable
  public String getCompactValueText() {
    return myCompactValueText;
  }

  @Override
  public String setValueLabelFailed(EvaluateException e) {
    final String label = setFailed(e);
    setValueLabel(label);
    return label;
  }

  @Override
  public Icon setValueIcon(Icon icon) {
    return myValueIcon = icon;
  }

  @Nullable
  public Icon getValueIcon() {
    return myValueIcon;
  }

  public String calcValueName() {
    String name = getName();
    NodeDescriptorNameAdjuster nameAdjuster = NodeDescriptorNameAdjuster.findFor(this);
    if (nameAdjuster != null) {
      return nameAdjuster.fixName(name, this);
    }
    return name;
  }

  @Nullable
  public String getDeclaredType() {
    return null;
  }

  @Override
  public void displayAs(NodeDescriptor descriptor) {
    if (descriptor instanceof ValueDescriptorImpl valueDescriptor) {
      myRenderer = valueDescriptor.myRenderer;
    }
    super.displayAs(descriptor);
  }

  public Renderer getLastRenderer() {
    return myRenderer != null ? myRenderer : myAutoRenderer;
  }

  public Renderer getLastLabelRenderer() {
    Renderer lastRenderer = getLastRenderer();
    if (lastRenderer instanceof CompoundReferenceRenderer) {
      lastRenderer = ((CompoundReferenceRenderer)lastRenderer).getLabelRenderer();
    }
    return lastRenderer;
  }

  public CompletableFuture<NodeRenderer> getChildrenRenderer(DebugProcessImpl debugProcess) {
    if (OnDemandRenderer.isOnDemandForced(debugProcess)) {
      return CompletableFuture.completedFuture(DebugProcessImpl.getDefaultRenderer(getValue()));
    }
    return getRenderer(debugProcess);
  }

  public CompletableFuture<NodeRenderer> getRenderer(DebugProcessImpl debugProcess) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    return DebuggerUtilsAsync.type(getValue())
      .thenCompose(type -> getRenderer(type, debugProcess));
  }

  protected final CompletableFuture<NodeRenderer> getRenderer(Type type, DebugProcessImpl debugProcess) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    CompletableFuture<Boolean> customCheck = CompletableFuture.completedFuture(false);
    if (type != null && myRenderer != null) {
      customCheck = myRenderer.isApplicableAsync(type);
    }
    return customCheck.thenCompose(custom -> {
      DebuggerManagerThreadImpl.assertIsManagerThread();
      if (custom) {
        return CompletableFuture.completedFuture(myRenderer);
      }
      else {
        return DebuggerUtilsAsync.reschedule(debugProcess.getAutoRendererAsync(type))
          .thenApply(r -> myAutoRenderer = r);
      }
    });
  }

  public void setRenderer(NodeRenderer renderer) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    myRenderer = renderer;
    myAutoRenderer = null;
  }


  //returns expression that evaluates tree to this descriptor
  @NotNull
  public CompletableFuture<PsiElement> getTreeEvaluation(JavaValue value, DebuggerContextImpl context) throws EvaluateException {
    JavaValue parent = value.getParent();
    if (parent != null) {
      ValueDescriptorImpl vDescriptor = parent.getDescriptor();

      return vDescriptor.getTreeEvaluation(parent, context).thenCompose(parentEvaluation -> {
        if (!(parentEvaluation instanceof PsiExpression)) {
          return CompletableFuture.completedFuture(null);
        }

        return vDescriptor.getChildrenRenderer(context.getDebugProcess())
          .thenApply(childrenRenderer -> {
            try {
              return ReadAction.compute(() -> DebuggerTreeNodeExpression.substituteThis(
                childrenRenderer.getChildValueExpression(new DebuggerTreeNodeMock(value), context),
                ((PsiExpression)parentEvaluation), vDescriptor.getValue()
              ));
            }
            catch (EvaluateException e) {
              throw new CompletionException(e);
            }
          });
      });
    }

    Promise<PsiElement> res;
    try {
      PsiElement result = ReadAction.nonBlocking(() -> getDescriptorEvaluation(context)).executeSynchronously();
      res = Promises.resolvedPromise(result);
    }
    catch (Exception wrapper) {
      if (!(wrapper.getCause() instanceof EvaluateException)) throw wrapper;
      if (!(wrapper.getCause() instanceof NeedMarkException e)) throw (EvaluateException)wrapper.getCause();

      XValueMarkers<?, ?> markers = DebuggerUtilsImpl.getValueMarkers(context.getDebugProcess());
      if (markers != null) {
        ValueMarkup existing = markers.getMarkup(value);
        String markName;
        Promise<Object> promise;
        if (existing != null) {
          markName = existing.getText();
          promise = Promises.resolvedPromise();
        }
        else {
          markName = e.getMarkName();
          promise = markers.markValue(value, new ValueMarkup(markName, new JBColor(0, 0), null));
        }
        res = promise.then(__ -> ReadAction.nonBlocking(() -> JavaPsiFacade.getElementFactory(myProject)
          .createExpressionFromText(markName + CodeFragmentFactoryContextWrapper.DEBUG_LABEL_SUFFIX,
                                    PositionUtil.getContextElement(context))).executeSynchronously());
      }
      else {
        res = Promises.resolvedPromise(null);
      }
    }
    return Promises.asCompletableFuture(res);
  }

  protected static class NeedMarkException extends EvaluateException {
    private final String myMarkName;

    public NeedMarkException(ObjectReference reference) {
      super(null);
      myMarkName = NamesUtils.getUniqueName(reference).replace("@", "");
    }

    @Override
    public Throwable fillInStackTrace() {
      return this;
    }

    public String getMarkName() {
      return myMarkName;
    }
  }

  private static class DebuggerTreeNodeMock implements DebuggerTreeNode {
    private final JavaValue value;

    DebuggerTreeNodeMock(JavaValue value) {
      this.value = value;
    }

    @Override
    public DebuggerTreeNode getParent() {
      return new DebuggerTreeNodeMock(value.getParent());
    }

    @Override
    public ValueDescriptorImpl getDescriptor() {
      return value.getDescriptor();
    }

    @Override
    public Project getProject() {
      return value.getProject();
    }

    @Override
    public void setRenderer(NodeRenderer renderer) {
    }
  }

  //returns expression that evaluates descriptor value
  //use 'this' to reference parent node
  //for ex. FieldDescriptorImpl should return
  //this.fieldName
  @Override
  public abstract PsiExpression getDescriptorEvaluation(DebuggerContext context) throws EvaluateException;

  public static String getIdLabel(ObjectReference objRef) {
    return calcIdLabel(objRef, null, null);
  }

  @Nullable
  public static String calcIdLabel(ValueDescriptor descriptor, @NotNull DescriptorLabelListener labelListener) {
    Value value = descriptor.getValue();
    if (!(value instanceof ObjectReference)) {
      return null;
    }
    return calcIdLabel((ObjectReference)value, descriptor, labelListener);
  }

  @Nullable
  private static String calcIdLabel(ObjectReference objRef,
                                    @Nullable ValueDescriptor descriptor,
                                    @Nullable DescriptorLabelListener labelListener) {
    final ClassRenderer classRenderer = NodeRendererSettings.getInstance().getClassRenderer();
    if (objRef instanceof StringReference && !classRenderer.SHOW_STRINGS_TYPE) {
      return null;
    }
    StringBuilder buf = new StringBuilder();
    final boolean showConcreteType =
      !classRenderer.SHOW_DECLARED_TYPE ||
      (!(objRef instanceof StringReference) && !(objRef instanceof ClassObjectReference) && !isEnumConstant(objRef));
    if (showConcreteType || classRenderer.SHOW_OBJECT_ID) {
      //buf.append('{');
      if (showConcreteType) {
        buf.append(classRenderer.renderTypeName(objRef.type().name()));
      }
      if (classRenderer.SHOW_OBJECT_ID) {
        buf.append('@');
        if (ApplicationManager.getApplication().isUnitTestMode()) {
          buf.append("uniqueID");
        }
        else {
          buf.append(objRef.uniqueID());
        }
      }
      //buf.append('}');
    }

    if (objRef instanceof ArrayReference) {
      int idx = buf.indexOf("[");
      if (idx >= 0) {
        if (labelListener == null || descriptor == null) {
          buf.insert(idx + 1, ((ArrayReference)objRef).length());
        }
        else {
          CompletableFuture<String> asyncId = DebuggerUtilsAsync.length((ArrayReference)objRef)
            .thenApply(length -> buf.insert(idx + 1, length).toString());
          if (asyncId.isDone()) {
            return asyncId.join();
          }
          else {
            asyncId.thenAccept(res -> {
              descriptor.setIdLabel(res);
              labelListener.labelChanged();
            });
          }
        }
      }
    }

    return buf.toString();
  }

  private static boolean isEnumConstant(final ObjectReference objRef) {
    try {
      Type type = objRef.type();
      return type instanceof ClassType && ((ClassType)type).isEnum();
    }
    catch (ObjectCollectedException ignored) {
    }
    return false;
  }

  public boolean canSetValue() {
    return isValueReady() && isLvalue();
  }

  public XValueModifier getModifier(JavaValue value) {
    return null;
  }

  public String getIdLabel() {
    return myIdLabel;
  }

  @Override
  public void setIdLabel(String idLabel) {
    myIdLabel = idLabel;
  }

  public String getValueLabel() {
    String label = getIdLabel();
    if (!StringUtil.isEmpty(label)) {
      return '{' + label + '}' + getValueText();
    }
    return getValueText();
  }

  @NotNull
  public String getValueText() {
    return StringUtil.notNullize(myValueText);
  }

  //Context is set to null
  @Override
  public void clear() {
    super.clear();
    setValueLabel("");
    myIsExpandable = false;
  }

  public boolean canMark() {
    if (!isValueReady()) {
      return false;
    }
    return getValue() instanceof ObjectReference;
  }

  public Project getProject() {
    return myProject;
  }

  @NotNull
  public String getDeclaredTypeLabel() {
    ClassRenderer classRenderer = NodeRendererSettings.getInstance().getClassRenderer();
    if (classRenderer.SHOW_DECLARED_TYPE) {
      String declaredType = getDeclaredType();
      if (!StringUtil.isEmpty(declaredType)) {
        return ": " + classRenderer.renderTypeName(declaredType);
      }
    }
    return "";
  }

  public EvaluationContextImpl getStoredEvaluationContext() {
    return myStoredEvaluationContext;
  }
}
