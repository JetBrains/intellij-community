// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.ui.impl.watch;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.DebuggerContext;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.engine.DebuggerManagerThreadImpl;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.JavaValue;
import com.intellij.debugger.engine.JavaValueModifier;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.PositionUtil;
import com.intellij.debugger.settings.NodeRendererSettings;
import com.intellij.debugger.settings.ViewsGeneralSettings;
import com.intellij.debugger.ui.tree.FieldDescriptor;
import com.intellij.debugger.ui.tree.NodeDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xdebugger.frame.XValueModifier;
import com.sun.jdi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FieldDescriptorImpl extends ValueDescriptorImpl implements FieldDescriptor{
  public static final String OUTER_LOCAL_VAR_FIELD_PREFIX = "val$";
  private final Field myField;
  private final ObjectReference myObject;
  private Boolean myIsPrimitive = null;
  private final boolean myIsStatic;

  public FieldDescriptorImpl(Project project, ObjectReference objRef, @NotNull Field field) {
    super(project);
    myObject = objRef;
    myField = field;
    myIsStatic = field.isStatic();
    setLvalue(!field.isFinal());
  }

  @Override
  public Field getField() {
    return myField;
  }

  @Override
  public ObjectReference getObject() {
    return myObject;
  }

  @Override
  public void setAncestor(NodeDescriptor oldDescriptor) {
    super.setAncestor(oldDescriptor);
    final Boolean isPrimitive = ((FieldDescriptorImpl)oldDescriptor).myIsPrimitive;
    if (isPrimitive != null) { // was cached
      // do not loose cached info
      myIsPrimitive = isPrimitive;
    }
  }


  @Override
  public boolean isPrimitive() {
    if (myIsPrimitive == null) {
      final Value value = getValue();
      if (value != null) {
        myIsPrimitive = super.isPrimitive();
      }
      else {
        myIsPrimitive = DebuggerUtils.isPrimitiveType(myField.typeName());
      }
    }
    return myIsPrimitive.booleanValue();
  }

  @Override
  public Value calcValue(EvaluationContextImpl evaluationContext) throws EvaluateException {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    try {
      if (myObject != null) {
        Value fieldValue = myObject.getValue(myField);
        if (populateExceptionStackTraceIfNeeded(fieldValue, evaluationContext)) {
          // re-read stacktrace value
          fieldValue = myObject.getValue(myField);
        }
        return fieldValue;
      }
      else {
        return myField.declaringType().getValue(myField);
      }
    }
    catch (ObjectCollectedException ignored) {
      throw EvaluateExceptionUtil.OBJECT_WAS_COLLECTED;
    }
  }

  private boolean populateExceptionStackTraceIfNeeded(Value value, EvaluationContextImpl evaluationContext) {
    if ("stackTrace".equals(getName()) &&
        ViewsGeneralSettings.getInstance().POPULATE_THROWABLE_STACKTRACE &&
        value instanceof ArrayReference &&
        ((ArrayReference)value).length() == 0 &&
        DebuggerUtils.instanceOf(myObject.type(), CommonClassNames.JAVA_LANG_THROWABLE)) {
      try {
        invokeExceptionGetStackTrace(myObject, evaluationContext);
        return true;
      }
      catch (Throwable e) {
        LOG.info(e); // catch all exceptions to ensure the method returns gracefully
      }
    }
    return false;
  }

  public boolean isStatic() {
    return myIsStatic;
  }

  @Override
  public String getName() {
    return myField.name();
  }

  @Override
  public String calcValueName() {
    String res = super.calcValueName();
    if (Boolean.TRUE.equals(getUserData(SHOW_DECLARING_TYPE))) {
      return NodeRendererSettings.getInstance().getClassRenderer().renderTypeName(myField.declaringType().name()) + "." + res;
    }
    return res;
  }

  public boolean isOuterLocalVariableValue() {
    try {
      return DebuggerUtils.isSynthetic(myField) && myField.name().startsWith(OUTER_LOCAL_VAR_FIELD_PREFIX);
    }
    catch (UnsupportedOperationException ignored) {
      return false;
    }
  }

  @Nullable
  @Override
  public String getDeclaredType() {
    return myField.typeName();
  }

  @Override
  public PsiExpression getDescriptorEvaluation(DebuggerContext context) throws EvaluateException {
    PsiElementFactory elementFactory = JavaPsiFacade.getInstance(myProject).getElementFactory();
    String fieldName;
    if(isStatic()) {
      String typeName = myField.declaringType().name().replace('$', '.');
      typeName = DebuggerTreeNodeExpression.normalize(typeName, PositionUtil.getContextElement(context), myProject);
      fieldName = typeName + "." + getName();
    }
    else {
      //noinspection HardCodedStringLiteral
      fieldName = isOuterLocalVariableValue()? StringUtil.trimStart(getName(), OUTER_LOCAL_VAR_FIELD_PREFIX) : "this." + getName();
    }
    try {
      return elementFactory.createExpressionFromText(fieldName, null);
    }
    catch (IncorrectOperationException e) {
      throw new EvaluateException(DebuggerBundle.message("error.invalid.field.name", getName()), e);
    }
  }

  @Override
  public XValueModifier getModifier(JavaValue value) {
    return new JavaValueModifier(value) {
      @Override
      protected void setValueImpl(@NotNull String expression, @NotNull XModificationCallback callback) {
        final DebuggerContextImpl debuggerContext = DebuggerManagerEx.getInstanceEx(getProject()).getContext();
        FieldDescriptorImpl fieldDescriptor = FieldDescriptorImpl.this;
        final Field field = fieldDescriptor.getField();
        if (!field.isStatic()) {
          final ObjectReference object = fieldDescriptor.getObject();
          if (object != null) {
            set(expression, callback, debuggerContext, new SetValueRunnable() {
              public void setValue(EvaluationContextImpl evaluationContext, Value newValue)
                throws ClassNotLoadedException, InvalidTypeException, EvaluateException {
                object.setValue(field, preprocessValue(evaluationContext, newValue, field.type()));
                update(debuggerContext);
              }

              public ReferenceType loadClass(EvaluationContextImpl evaluationContext, String className) throws
                                                                                                        InvocationException,
                                                                                                        ClassNotLoadedException,
                                                                                                        IncompatibleThreadStateException,
                                                                                                        InvalidTypeException,
                                                                                                        EvaluateException {
                return evaluationContext.getDebugProcess().loadClass(evaluationContext, className, field.declaringType().classLoader());
              }
            });
          }
        }
        else {
          // field is static
          ReferenceType refType = field.declaringType();
          if (refType instanceof ClassType) {
            final ClassType classType = (ClassType)refType;
            set(expression, callback, debuggerContext, new SetValueRunnable() {
              public void setValue(EvaluationContextImpl evaluationContext, Value newValue)
                throws ClassNotLoadedException, InvalidTypeException, EvaluateException {
                classType.setValue(field, preprocessValue(evaluationContext, newValue, field.type()));
                update(debuggerContext);
              }

              public ReferenceType loadClass(EvaluationContextImpl evaluationContext, String className) throws
                                                                                                        InvocationException,
                                                                                                        ClassNotLoadedException,
                                                                                                        IncompatibleThreadStateException,
                                                                                                        InvalidTypeException,
                                                                                                        EvaluateException {
                return evaluationContext.getDebugProcess().loadClass(evaluationContext, className,
                                                                     field.declaringType().classLoader());
              }
            });
          }
        }
      }
    };
  }
}
