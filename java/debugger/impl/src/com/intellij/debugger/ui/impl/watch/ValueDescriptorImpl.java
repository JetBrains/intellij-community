// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.ui.impl.watch;

import com.intellij.Patches;
import com.intellij.debugger.DebuggerContext;
import com.intellij.debugger.engine.*;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.jdi.VirtualMachineProxyImpl;
import com.intellij.debugger.settings.NodeRendererSettings;
import com.intellij.debugger.ui.overhead.OverheadTimings;
import com.intellij.debugger.ui.tree.DebuggerTreeNode;
import com.intellij.debugger.ui.tree.NodeDescriptor;
import com.intellij.debugger.ui.tree.NodeDescriptorNameAdjuster;
import com.intellij.debugger.ui.tree.ValueDescriptor;
import com.intellij.debugger.ui.tree.render.*;
import com.intellij.debugger.ui.tree.render.Renderer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.util.StringBuilderSpinAllocator;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.xdebugger.frame.XValueModifier;
import com.intellij.xdebugger.impl.ui.tree.ValueMarkup;
import com.sun.jdi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collections;
import java.util.Map;

public abstract class ValueDescriptorImpl extends NodeDescriptorImpl implements ValueDescriptor{
  protected final Project myProject;

  NodeRenderer myRenderer = null;

  NodeRenderer myAutoRenderer = null;

  private Value myValue;
  private boolean myValueReady;

  private EvaluateException myValueException;
  protected EvaluationContextImpl myStoredEvaluationContext = null;

  private String myIdLabel;
  private String myValueText;
  private boolean myFullValue = false;

  @Nullable
  private Icon myValueIcon;

  protected boolean myIsNew = true;
  private boolean myIsDirty = false;
  private boolean myIsLvalue = false;
  private boolean myIsExpandable;

  private boolean myShowIdLabel = true;

  protected ValueDescriptorImpl(Project project, Value value) {
    myProject = project;
    myValue = value;
    myValueReady = true;
  }

  protected ValueDescriptorImpl(Project project) {
    myProject = project;
  }

  private void assertValueReady() {
    if (!myValueReady) {
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
    return myShowIdLabel && Registry.is("debugger.showTypes");
  }

  public void setShowIdLabel(boolean showIdLabel) {
    myShowIdLabel = showIdLabel;
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
        evalContext.getDebugProcess().getManagerThread().invoke(new SuspendContextCommandImpl(evalContext.getSuspendContext()) {
          @Override
          public void contextAction(@NotNull SuspendContextImpl suspendContext) throws Exception {
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

      if(!myIsNew) {
        try {
          if (myValue instanceof DoubleValue && Double.isNaN(((DoubleValue)myValue).doubleValue())) {
            myIsDirty = !(value instanceof DoubleValue);
          }
          else if (myValue instanceof FloatValue && Float.isNaN(((FloatValue)myValue).floatValue())) {
            myIsDirty = !(value instanceof FloatValue);
          }
          else {
            myIsDirty = (value == null) ? myValue != null : !value.equals(myValue);
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
      myValueReady = true;
    }

    myIsNew = false;
  }

  protected boolean isPrintExceptionToConsole() {
    return true;
  }

  @Nullable
  protected static Value invokeExceptionGetStackTrace(ObjectReference exceptionObj, EvaluationContextImpl evaluationContext)
    throws EvaluateException {
    Method method = ((ClassType)exceptionObj.referenceType()).concreteMethodByName("getStackTrace", "()[Ljava/lang/StackTraceElement;");
    if (method != null) {
      return evaluationContext.getDebugProcess().invokeMethod(evaluationContext, exceptionObj, method, Collections.emptyList());
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
          DebugProcessImpl process = evaluationContext.getDebugProcess();
          ArrayReference traceArray = (ArrayReference)trace;
          process.printToConsole(DebuggerUtils.getValueAsString(evaluationContext, exceptionObj) + "\n");
          for (Value stackElement : traceArray.getValues()) {
            process.printToConsole("\tat " + DebuggerUtils.getValueAsString(evaluationContext, stackElement) + "\n");
          }
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
    if (!myValueReady) {
      ValueDescriptorImpl other = (ValueDescriptorImpl)oldDescriptor;
      if (other.myValueReady) {
        myValue = other.getValue();
        myValueReady = true;
      }
    }
  }

  protected void setLvalue(boolean value) {
    myIsLvalue = value;
  }

  @Override
  protected String calcRepresentation(EvaluationContextImpl context, DescriptorLabelListener labelListener){
    DebuggerManagerThreadImpl.assertIsManagerThread();

    DebugProcessImpl debugProcess = context.getDebugProcess();
    NodeRenderer renderer = getRenderer(debugProcess);

    EvaluateException valueException = myValueException;
    myIsExpandable = (valueException == null || valueException.getExceptionFromTargetVM() != null) &&
                     getChildrenRenderer(debugProcess).isExpandable(getValue(), context, this);

    try {
      setValueIcon(renderer.calcValueIcon(this, context, labelListener));
    }
    catch (EvaluateException e) {
      LOG.info(e);
      setValueIcon(null);
    }

    String label;
    if (valueException == null) {
      long start = renderer instanceof NodeRendererImpl && ((NodeRendererImpl)renderer).hasOverhead() ? System.currentTimeMillis() : 0;
      try {
        label = renderer.calcLabel(this, context, labelListener);
      }
      catch (EvaluateException e) {
        label = setValueLabelFailed(e);
      }
      finally {
        if (start > 0) {
          OverheadTimings.add(debugProcess, new NodeRendererImpl.Overhead((NodeRendererImpl)renderer), 1, System.currentTimeMillis() - start);
        }
      }
    }
    else {
      label = setValueLabelFailed(valueException);
    }

    setValueLabel(label);

    return ""; // we have overridden getLabel
  }

  @Override
  public String getLabel() {
    return calcValueName() + getDeclaredTypeLabel() + " = " + getValueLabel();
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
      public NodeRenderer getRenderer(DebugProcessImpl debugProcess) {
        return ValueDescriptorImpl.this.getRenderer(debugProcess);
      }

      @Override
      public <T> T getUserData(Key<T> key) {
        return ValueDescriptorImpl.this.getUserData(key);
      }
    };
    descriptor.myFullValue = true;
    return descriptor;
  }

  @Override
  public void setValueLabel(@NotNull String label) {
    label = myFullValue ? label : DebuggerUtilsEx.truncateString(label);

    Value value = myValueReady ? getValue() : null;
    NodeRendererImpl lastRenderer = (NodeRendererImpl)getLastRenderer();
    EvaluationContextImpl evalContext = myStoredEvaluationContext;
    String labelId = myValueReady && evalContext != null && lastRenderer != null &&
                     !evalContext.getSuspendContext().isResumed() ?
                     lastRenderer.getIdLabel(value, evalContext.getDebugProcess()) : null;
    myValueText = label;
    myIdLabel = isShowIdLabel() ? labelId : null;
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
    if (descriptor instanceof ValueDescriptorImpl) {
      ValueDescriptorImpl valueDescriptor = (ValueDescriptorImpl)descriptor;
      myRenderer = valueDescriptor.myRenderer;
    }
    super.displayAs(descriptor);
  }

  public Renderer getLastRenderer() {
    return myRenderer != null ? myRenderer: myAutoRenderer;
  }

  public NodeRenderer getChildrenRenderer(DebugProcessImpl debugProcess) {
    return OnDemandRenderer.isOnDemandForced(debugProcess) ? DebugProcessImpl.getDefaultRenderer(getValue()) : getRenderer(debugProcess);
  }

  public NodeRenderer getRenderer(DebugProcessImpl debugProcess) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    Type type = getType();
    if(type != null && myRenderer != null && myRenderer.isApplicable(type)) {
      return myRenderer;
    }

    myAutoRenderer = debugProcess.getAutoRenderer(this);
    return myAutoRenderer;
  }

  public void setRenderer(NodeRenderer renderer) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    myRenderer = renderer;
    myAutoRenderer = null;
  }

  //returns expression that evaluates tree to this descriptor
  @Nullable
  public PsiElement getTreeEvaluation(JavaValue value, DebuggerContextImpl context) throws EvaluateException {
    JavaValue parent = value.getParent();
    if (parent != null) {
      ValueDescriptorImpl vDescriptor = parent.getDescriptor();
      PsiElement parentEvaluation = vDescriptor.getTreeEvaluation(parent, context);

      if (!(parentEvaluation instanceof PsiExpression)) {
        return null;
      }

      return DebuggerTreeNodeExpression.substituteThis(
        vDescriptor.getChildrenRenderer(context.getDebugProcess()).getChildValueExpression(new DebuggerTreeNodeMock(value), context),
        ((PsiExpression)parentEvaluation), vDescriptor.getValue()
      );
    }

    return getDescriptorEvaluation(context);
  }

  private static class DebuggerTreeNodeMock implements DebuggerTreeNode {
    private final JavaValue value;

    public DebuggerTreeNodeMock(JavaValue value) {
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
    final ClassRenderer classRenderer = NodeRendererSettings.getInstance().getClassRenderer();
    if (objRef instanceof StringReference && !classRenderer.SHOW_STRINGS_TYPE) {
      return null;
    }
    StringBuilder buf = StringBuilderSpinAllocator.alloc();
    try {
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
          if(ApplicationManager.getApplication().isUnitTestMode()) {
            //noinspection HardCodedStringLiteral
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
        if(idx >= 0) {
          buf.insert(idx + 1, Integer.toString(((ArrayReference)objRef).length()));
        }
      }

      return buf.toString();
    }
    finally {
      StringBuilderSpinAllocator.dispose(buf);
    }
  }

  private static boolean isEnumConstant(final ObjectReference objRef) {
    try {
      Type type = objRef.type();
      return type instanceof ClassType && ((ClassType)type).isEnum();
    } catch (ObjectCollectedException ignored) {}
    return false;
  }

  public boolean canSetValue() {
    return myValueReady && !myIsSynthetic && isLvalue();
  }

  public XValueModifier getModifier(JavaValue value) {
    return null;
  }

  @NotNull
  public String getIdLabel() {
    return StringUtil.notNullize(myIdLabel);
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

  @Override
  @Nullable
  public ValueMarkup getMarkup(final DebugProcess debugProcess) {
    final Value value = getValue();
    if (value instanceof ObjectReference) {
      final ObjectReference objRef = (ObjectReference)value;
      final Map<ObjectReference, ValueMarkup> map = getMarkupMap(debugProcess);
      if (map != null) {
        return map.get(objRef);
      }
    }
    return null;
  }

  @Override
  public void setMarkup(final DebugProcess debugProcess, @Nullable final ValueMarkup markup) {
    final Value value = getValue();
    if (value instanceof ObjectReference) {
      final Map<ObjectReference, ValueMarkup> map = getMarkupMap(debugProcess);
      if (map != null) {
        final ObjectReference objRef = (ObjectReference)value;
        if (markup != null) {
          map.put(objRef, markup);
        }
        else {
          map.remove(objRef);
        }
      }
    }
  }

  public boolean canMark() {
    if (!myValueReady) {
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
