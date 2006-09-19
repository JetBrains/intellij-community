/*
 * Class FieldEvaluator
 * @author Jeka
 */
package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.engine.JVMName;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.ui.impl.watch.FieldDescriptorImpl;
import com.intellij.debugger.ui.impl.watch.NodeDescriptorImpl;
import com.intellij.openapi.project.Project;
import com.sun.jdi.*;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;

public class FieldEvaluator implements Evaluator {
  private Evaluator myObjectEvaluator;
  private JVMName myClassName;
  private String myFieldName;
  private Object myEvaluatedQualifier;
  private Field myEvaluatedField;

  public FieldEvaluator(Evaluator objectEvaluator, JVMName contextClass, String fieldName) {
    myObjectEvaluator = objectEvaluator;
    myClassName = contextClass;
    myFieldName = fieldName;
  }

  @Nullable
  private Field findField(Type t, final EvaluationContextImpl context) throws EvaluateException {
    if(t instanceof ClassType) {
      ClassType cls = (ClassType) t;
      if(cls.name().equals(getClassName(context))) {
        return cls.fieldByName(myFieldName);
      }
      for (Iterator iterator = cls.interfaces().iterator(); iterator.hasNext();) {
        InterfaceType interfaceType = (InterfaceType) iterator.next();
        Field field = findField(interfaceType, context);
        if(field != null) {
          return field;
        }
      }
      return findField(cls.superclass(), context);
    }
    else if(t instanceof InterfaceType) {
      InterfaceType iface = (InterfaceType) t;
      if(iface.name().equals(getClassName(context))) {
        return iface.fieldByName(myFieldName);
      }
      for (Iterator iterator = iface.superinterfaces().iterator(); iterator.hasNext();) {
        InterfaceType interfaceType = (InterfaceType) iterator.next();
        Field field = findField(interfaceType, context);
        if(field != null) {
          return field;
        }
      }
    }
    return null;
  }

  public Object evaluate(EvaluationContextImpl context) throws EvaluateException {
    myEvaluatedField = null;
    myEvaluatedQualifier = null;
    Object object = myObjectEvaluator.evaluate(context);

    return evaluateField(object, context);

  }

  private Object evaluateField(Object object, EvaluationContextImpl context) throws EvaluateException {
    if (object instanceof ReferenceType) {
      ReferenceType refType = (ReferenceType)object;
      Field field = findField(refType, context);
      if (field == null || !field.isStatic()) {
        field = refType.fieldByName(myFieldName);
      }
      if (field == null || !field.isStatic()) {
        throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.no.static.field", myFieldName));
      }
      myEvaluatedField = field;
      myEvaluatedQualifier = refType;
      return refType.getValue(field);
    }

    if (object instanceof ObjectReference) {
      ObjectReference objRef = (ObjectReference)object;
      ReferenceType refType = objRef.referenceType();
      if (!(refType instanceof ClassType || refType instanceof ArrayType)) {
        throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.class.or.array.expected", myFieldName));
      }

      // expressions like 'array.length' must be treated separately
      //noinspection HardCodedStringLiteral
      if (objRef instanceof ArrayReference && "length".equals(myFieldName)) {
        //noinspection HardCodedStringLiteral
        return DebuggerUtilsEx.createValue(
          context.getDebugProcess().getVirtualMachineProxy(),
          "int",
          ((ArrayReference)objRef).length()
        );
      }

      Field field = findField(refType, context);
      if (field == null) {
        field = refType.fieldByName(myFieldName);
      }

      if (field == null) {
        throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.no.instance.field", myFieldName));
      }
      myEvaluatedQualifier = field.isStatic()? (Object)refType : (Object)objRef;
      myEvaluatedField = field;
      return field.isStatic()? refType.getValue(field) : objRef.getValue(field);
    }

    if(object == null) {
      throw EvaluateExceptionUtil.createEvaluateException(new NullPointerException());
    }

    throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.evaluating.field", myFieldName));
  }

  public Modifier getModifier() {
    Modifier modifier = null;
    if (myEvaluatedField != null && (myEvaluatedQualifier instanceof ClassType || myEvaluatedQualifier instanceof ObjectReference)) {
      modifier = new Modifier() {
        public boolean canInspect() {
          return myEvaluatedQualifier instanceof ObjectReference;
        }

        public boolean canSetValue() {
          return true;
        }

        public void setValue(Value value) throws ClassNotLoadedException, InvalidTypeException {
          if (myEvaluatedQualifier instanceof ReferenceType) {
            ClassType classType = (ClassType)myEvaluatedQualifier;
            classType.setValue(myEvaluatedField, value);
          }
          else {
            ObjectReference objRef = (ObjectReference)myEvaluatedQualifier;
            objRef.setValue(myEvaluatedField, value);
          }
        }

        public Type getExpectedType() throws ClassNotLoadedException {
          return myEvaluatedField.type();
        }

        public NodeDescriptorImpl getInspectItem(Project project) {
          if(myEvaluatedQualifier instanceof ObjectReference) {
            return new FieldDescriptorImpl(project, (ObjectReference)myEvaluatedQualifier, myEvaluatedField);
          } else
            return null;
        }
      };
    }
    return modifier;
  }

  private String myClassNameCached = null;
  private String getClassName(final EvaluationContextImpl context) throws EvaluateException {
    if (myClassNameCached == null) {
      myClassNameCached = myClassName.getName(context.getDebugProcess());
    }
    return myClassNameCached;
  }
}
