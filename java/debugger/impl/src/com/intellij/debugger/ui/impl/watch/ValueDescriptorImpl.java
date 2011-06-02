/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.debugger.ui.impl.watch;

import com.intellij.Patches;
import com.intellij.debugger.DebuggerContext;
import com.intellij.debugger.engine.DebugProcess;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.DebuggerManagerThreadImpl;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.jdi.VirtualMachineProxyImpl;
import com.intellij.debugger.settings.NodeRendererSettings;
import com.intellij.debugger.ui.tree.NodeDescriptor;
import com.intellij.debugger.ui.tree.ValueDescriptor;
import com.intellij.debugger.ui.tree.render.ClassRenderer;
import com.intellij.debugger.ui.tree.render.DescriptorLabelListener;
import com.intellij.debugger.ui.tree.render.NodeRenderer;
import com.intellij.debugger.ui.tree.render.Renderer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiExpression;
import com.intellij.util.StringBuilderSpinAllocator;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.xdebugger.impl.ui.tree.ValueMarkup;
import com.sun.jdi.*;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public abstract class ValueDescriptorImpl extends NodeDescriptorImpl implements ValueDescriptor{
  protected final Project myProject;

  NodeRenderer myRenderer = null;

  NodeRenderer myAutoRenderer = null;

  private Value myValue;
  private EvaluateException myValueException;
  protected EvaluationContextImpl myStoredEvaluationContext = null;

  private String myValueLabel;
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
  }

  protected ValueDescriptorImpl(Project project) {
    myProject = project;
  }

  public boolean isArray() { 
    return myValue instanceof ArrayReference; 
  }
  
  public boolean isDirty() { 
    return myIsDirty; 
  }
  
  public boolean isLvalue() { 
    return myIsLvalue; 
  }
  
  public boolean isNull() { 
    return myValue == null; 
  }
  
  public boolean isPrimitive() { 
    return myValue instanceof PrimitiveValue; 
  }
  
  public boolean isValueValid() {
    return myValueException == null;
  }

  public boolean isShowIdLabel() {
    return myShowIdLabel;
  }

  public void setShowIdLabel(boolean showIdLabel) {
    myShowIdLabel = showIdLabel;
  }

  public Value getValue() {
    // the following code makes sence only if we do not use ObjectReference.enableCollection() / disableCollection()
    // to keep temporary objects
    if (Patches.IBM_JDK_DISABLE_COLLECTION_BUG && myStoredEvaluationContext != null && !myStoredEvaluationContext.getSuspendContext().isResumed() &&
        myValue instanceof ObjectReference && VirtualMachineProxyImpl.isCollected((ObjectReference)myValue)) {

      final Semaphore semaphore = new Semaphore();
      semaphore.down();
      myStoredEvaluationContext.getDebugProcess().getManagerThread().invoke(new SuspendContextCommandImpl(myStoredEvaluationContext.getSuspendContext()) {
        public void contextAction() throws Exception {
          // re-setting the context will cause value recalculation
          try {
            setContext(myStoredEvaluationContext);
          }
          finally {
            semaphore.up();
          }
        }
      });
      semaphore.waitFor();
    }
    
    return myValue; 
  }
  
  public boolean isExpandable() {
    return myIsExpandable;
  }

  public abstract Value calcValue(EvaluationContextImpl evaluationContext) throws EvaluateException;

  public final void setContext(EvaluationContextImpl evaluationContext) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    if (Patches.IBM_JDK_DISABLE_COLLECTION_BUG) {
      myStoredEvaluationContext = evaluationContext;
    }
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
        catch (ObjectCollectedException e) {
          myIsDirty = true;
        }
      }
      myValue = value;
      myValueException = null;
    }
    catch (EvaluateException e) {
      myValueException = e;
      myValue = getTargetExceptionWithStackTraceFilled(evaluationContext, e);
      myIsExpandable = false;
    }

    myIsNew = false;
  }

  @Nullable
  private static ObjectReference getTargetExceptionWithStackTraceFilled(final EvaluationContextImpl evaluationContext, EvaluateException ex){
    final ObjectReference exceptionObj = ex.getExceptionFromTargetVM();
    if (exceptionObj != null && evaluationContext != null) {
      try {
        final ReferenceType refType = exceptionObj.referenceType();
        final List<Method> methods = refType.methodsByName("getStackTrace", "()[Ljava/lang/StackTraceElement;");
        if (methods.size() > 0) {
          final DebugProcessImpl process = evaluationContext.getDebugProcess();
          process.invokeMethod(evaluationContext, exceptionObj, methods.get(0), Collections.emptyList());
          
          // print to console as well
          
          final Field traceField = refType.fieldByName("stackTrace");
          final Value trace = traceField != null? exceptionObj.getValue(traceField) : null; 
          if (trace instanceof ArrayReference) {
            final ArrayReference traceArray = (ArrayReference)trace;
            final Type componentType = ((ArrayType)traceArray.referenceType()).componentType();
            if (componentType instanceof ClassType) {
              process.printToConsole(DebuggerUtils.getValueAsString(evaluationContext, exceptionObj));
              process.printToConsole("\n");
              for (Value stackElement : traceArray.getValues()) {
                process.printToConsole("\tat ");
                process.printToConsole(DebuggerUtils.getValueAsString(evaluationContext, stackElement));
                process.printToConsole("\n");
              }
            }
          }
        }
      }
      catch (EvaluateException ignored) {
      }
      catch (ClassNotLoadedException ignored) {
      }
    }
    return exceptionObj;
  }

  public void setAncestor(NodeDescriptor oldDescriptor) {
    super.setAncestor(oldDescriptor);
    myIsNew = false;
    myValue = ((ValueDescriptorImpl)oldDescriptor).getValue();
  }

  protected void setLvalue(boolean value) {
    myIsLvalue = value;
  }

  protected String calcRepresentation(EvaluationContextImpl context, DescriptorLabelListener labelListener){
    DebuggerManagerThreadImpl.assertIsManagerThread();

    final NodeRenderer renderer = getRenderer(context.getDebugProcess());

    final EvaluateException valueException = myValueException;
    myIsExpandable = (valueException == null || valueException.getExceptionFromTargetVM() != null) && renderer.isExpandable(getValue(), context, this);

    try {
      setValueIcon(renderer.calcValueIcon(this, context, labelListener));
    }
    catch (EvaluateException e) {
      LOG.info(e);
      setValueIcon(null);
    }

    String label;
    if (valueException == null) {
      try {
        label = renderer.calcLabel(this, context, labelListener);
      }
      catch (EvaluateException e) {
        label = setValueLabelFailed(e);
      }
    }
    else {
      label = setValueLabelFailed(valueException);
    }

    return setValueLabel(label);
  }

  private String getCustomLabel(String label) {
    //translate only strings in quotes
    final StringBuilder buf = StringBuilderSpinAllocator.alloc();
    try {
      final Value value = getValue();
      if(isShowIdLabel() && value instanceof ObjectReference) {
        final String idLabel = getIdLabel((ObjectReference)value);
        if(!label.startsWith(idLabel)) {
          buf.append(idLabel);
        }
      }
      if(label == null) {
        //noinspection HardCodedStringLiteral
        buf.append("null");
      }
      else {
        buf.append(label);
      }
      return buf.toString();
    }
    finally {
      StringBuilderSpinAllocator.dispose(buf);
    }
  }


  public String setValueLabel(String label) {
    final String customLabel = getCustomLabel(label);
    myValueLabel = customLabel;
    return setLabel(calcValueName() + " = " + customLabel);
  }

  public String setValueLabelFailed(EvaluateException e) {
    final String label = setFailed(e);
    setValueLabel(label);
    return label;
  }

  public Icon setValueIcon(Icon icon) {
    return myValueIcon = icon;
  }

  @Nullable
  public Icon getValueIcon() {
    return myValueIcon;
  }

  public abstract String calcValueName();

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

  @Nullable
  public Type getType() {
    Value value = getValue();
    return value != null ? value.type() : null;
  }

  public NodeRenderer getRenderer (DebugProcessImpl debugProcess) {
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
  public PsiExpression getTreeEvaluation(DebuggerTreeNodeImpl debuggerTreeNode, DebuggerContextImpl context) throws EvaluateException {
    if(debuggerTreeNode.getParent() != null && debuggerTreeNode.getParent().getDescriptor() instanceof ValueDescriptor) {
      final NodeDescriptorImpl descriptor = debuggerTreeNode.getParent().getDescriptor();
      final ValueDescriptorImpl vDescriptor = ((ValueDescriptorImpl)descriptor);
      final PsiExpression parentEvaluation = vDescriptor.getTreeEvaluation(debuggerTreeNode.getParent(), context);

      if (parentEvaluation == null) {
        return null;
      }

      return DebuggerTreeNodeExpression.substituteThis(
        vDescriptor.getRenderer(context.getDebugProcess()).getChildValueExpression(debuggerTreeNode, context),
        parentEvaluation, vDescriptor.getValue()
      );
    }

    return getDescriptorEvaluation(context);
  }

  //returns expression that evaluates descriptor value
  //use 'this' to reference parent node
  //for ex. FieldDescriptorImpl should return
  //this.fieldName
  public abstract PsiExpression getDescriptorEvaluation(DebuggerContext context) throws EvaluateException;

  public static String getIdLabel(ObjectReference objRef) {
    StringBuilder buf = StringBuilderSpinAllocator.alloc();
    try {
      final ClassRenderer classRenderer = NodeRendererSettings.getInstance().getClassRenderer();
      final boolean showConcreteType =
        !classRenderer.SHOW_DECLARED_TYPE ||
        (!(objRef instanceof StringReference) && !(objRef instanceof ClassObjectReference) && !isEnumConstant(objRef));
      if (showConcreteType || classRenderer.SHOW_OBJECT_ID) {
        buf.append('{');
        if (showConcreteType) {
          buf.append(objRef.type().name());
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
        buf.append('}');
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
    final Type type = objRef.type();
    return type instanceof ClassType && ((ClassType)type).isEnum();
  }

  public boolean canSetValue() {
    return !myIsSynthetic && isLvalue();
  }

  public String getValueLabel() {
    return myValueLabel;
  }

  //Context is set to null
  public void clear() {
    super.clear();
    setValueLabel("");
    myIsExpandable = false;
  }

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

}