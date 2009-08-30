/**
 * class FilteredRequestor
 * @author Jeka
 */
package com.intellij.debugger.ui.breakpoints;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.DebuggerInvocationUtil;
import com.intellij.debugger.EvaluatingComputable;
import com.intellij.debugger.InstanceFilter;
import com.intellij.debugger.engine.ContextUtil;
import com.intellij.debugger.engine.evaluation.*;
import com.intellij.debugger.engine.evaluation.expression.EvaluatorBuilderImpl;
import com.intellij.debugger.engine.evaluation.expression.ExpressionEvaluator;
import com.intellij.debugger.engine.requests.LocatableEventRequestor;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.psi.PsiElement;
import com.intellij.ui.classFilter.ClassFilter;
import com.sun.jdi.BooleanValue;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.VMDisconnectedException;
import com.sun.jdi.Value;
import com.sun.jdi.event.LocatableEvent;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.List;

public abstract class FilteredRequestor implements LocatableEventRequestor, JDOMExternalizable {

  public String  SUSPEND_POLICY = DebuggerSettings.SUSPEND_ALL;

  public boolean COUNT_FILTER_ENABLED     = false;
  public int COUNT_FILTER = 0;

  public boolean CONDITION_ENABLED        = false;
  private TextWithImports myCondition;

  public boolean CLASS_FILTERS_ENABLED    = false;
  private ClassFilter[] myClassFilters          = ClassFilter.EMPTY_ARRAY;
  private ClassFilter[] myClassExclusionFilters = ClassFilter.EMPTY_ARRAY;

  public boolean INSTANCE_FILTERS_ENABLED = false;
  private InstanceFilter[] myInstanceFilters  = InstanceFilter.EMPTY_ARRAY;

  @NonNls private static final String FILTER_OPTION_NAME = "filter";
  @NonNls private static final String EXCLUSION_FILTER_OPTION_NAME = "exclusion_filter";
  @NonNls private static final String INSTANCE_ID_OPTION_NAME = "instance_id";
  @NonNls private static final String CONDITION_OPTION_NAME = "CONDITION";
  protected final Project myProject;

  public FilteredRequestor(Project project) {
    myProject = project;
    myCondition = new TextWithImportsImpl(CodeFragmentKind.EXPRESSION, "");
  }

  public InstanceFilter[] getInstanceFilters() {
    return myInstanceFilters;
  }

  public void setInstanceFilters(InstanceFilter[] instanceFilters) {
    myInstanceFilters = instanceFilters != null? instanceFilters : InstanceFilter.EMPTY_ARRAY;
  }

  /**
   * @return true if the ID was added or false otherwise
   */
  private boolean hasObjectID(long id) {
    for (InstanceFilter instanceFilter : myInstanceFilters) {
      if (instanceFilter.getId() == id) {
        return true;
      }
    }
    return false;
  }

  protected void addInstanceFilter(long l) {
    final InstanceFilter[] filters = new InstanceFilter[myInstanceFilters.length + 1];
    System.arraycopy(myInstanceFilters, 0, filters, 0, myInstanceFilters.length);
    filters[myInstanceFilters.length] = InstanceFilter.create(String.valueOf(l));
    myInstanceFilters = filters;
  }

  public final ClassFilter[] getClassFilters() {
    return myClassFilters;
  }

  public final void setClassFilters(ClassFilter[] classFilters) {
    myClassFilters = classFilters != null? classFilters : ClassFilter.EMPTY_ARRAY;
  }

  public ClassFilter[] getClassExclusionFilters() {
    return myClassExclusionFilters;
  }

  public void setClassExclusionFilters(ClassFilter[] classExclusionFilters) {
    myClassExclusionFilters = classExclusionFilters != null? classExclusionFilters : ClassFilter.EMPTY_ARRAY;
  }

  public void readExternal(Element parentNode) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, parentNode);
    String condition = JDOMExternalizerUtil.readField(parentNode, CONDITION_OPTION_NAME);
    if (condition != null) {
      setCondition(new TextWithImportsImpl(CodeFragmentKind.EXPRESSION, condition));
    }

    myClassFilters = DebuggerUtilsEx.readFilters(parentNode.getChildren(FILTER_OPTION_NAME));
    myClassExclusionFilters = DebuggerUtilsEx.readFilters(parentNode.getChildren(EXCLUSION_FILTER_OPTION_NAME));

    final ClassFilter [] instanceFilters = DebuggerUtilsEx.readFilters(parentNode.getChildren(INSTANCE_ID_OPTION_NAME));
    final List<InstanceFilter> iFilters = new ArrayList<InstanceFilter>(instanceFilters.length);

    for (ClassFilter instanceFilter : instanceFilters) {
      try {
        iFilters.add(InstanceFilter.create(instanceFilter));
      }
      catch (Exception e) {
      }
    }
    myInstanceFilters = iFilters.isEmpty() ? InstanceFilter.EMPTY_ARRAY : iFilters.toArray(new InstanceFilter[iFilters.size()]);
  }

  public void writeExternal(Element parentNode) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, parentNode);
    JDOMExternalizerUtil.writeField(parentNode, CONDITION_OPTION_NAME, getCondition().toString());
    DebuggerUtilsEx.writeFilters(parentNode, FILTER_OPTION_NAME, myClassFilters);
    DebuggerUtilsEx.writeFilters(parentNode, EXCLUSION_FILTER_OPTION_NAME, myClassExclusionFilters);
    DebuggerUtilsEx.writeFilters(parentNode, INSTANCE_ID_OPTION_NAME, InstanceFilter.createClassFilters(myInstanceFilters));
  }

  public boolean evaluateCondition(final EvaluationContextImpl context, LocatableEvent event) throws EvaluateException {
    if(COUNT_FILTER_ENABLED) {
      context.getDebugProcess().getVirtualMachineProxy().suspend();
      context.getDebugProcess().getRequestsManager().deleteRequest(this);
      ((Breakpoint)this).createRequest(context.getDebugProcess());
      context.getDebugProcess().getVirtualMachineProxy().resume();
    }
    if (INSTANCE_FILTERS_ENABLED) {
      Value value = context.getThisObject();
      if (value != null) {  // non-static
        ObjectReference reference = (ObjectReference)value;
        if(!hasObjectID(reference.uniqueID())) return false;
      }
    }

    if (CONDITION_ENABLED && getCondition() != null && !"".equals(getCondition().getText())) {
      try {
        ExpressionEvaluator evaluator = DebuggerInvocationUtil.commitAndRunReadAction(context.getProject(), new EvaluatingComputable<ExpressionEvaluator>() {
          public ExpressionEvaluator compute() throws EvaluateException {
            return EvaluatorBuilderImpl.getInstance().build(getCondition(), getEvaluationElement(), ContextUtil.getSourcePosition(context));
          }
        });
        Value value = evaluator.evaluate(context);
        if (!(value instanceof BooleanValue)) {
          throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.boolean.expected"));
        }
        if(!((BooleanValue)value).booleanValue()) {
          return false;
        }
      }
      catch (EvaluateException ex) {
        if(ex.getCause() instanceof VMDisconnectedException) {
          return false;
        }
        throw EvaluateExceptionUtil.createEvaluateException(
          DebuggerBundle.message("error.failed.evaluating.breakpoint.condition", getCondition(), ex.getMessage())
        );
      }
      return true;
    }

    return true;
  }

  public abstract PsiElement getEvaluationElement();

  public TextWithImports getCondition() {
    return myCondition;
  }

  public void setCondition(TextWithImports condition) {
    myCondition = condition;
  }

  public Project getProject() {
    return myProject;
  }
}