// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * Class FieldEvaluator
 * @author Jeka
 */
package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.engine.JVMNameUtil;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.ui.impl.watch.FieldDescriptorImpl;
import com.intellij.debugger.ui.impl.watch.NodeDescriptorImpl;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiUtil;
import com.sun.jdi.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FieldEvaluator implements Evaluator {
  private final Evaluator myObjectEvaluator;
  private final TargetClassFilter myTargetClassFilter;
  private final String myFieldName;
  private Object myEvaluatedQualifier;
  private Field myEvaluatedField;

  public interface TargetClassFilter {
    TargetClassFilter ALL = refType -> true;
    boolean acceptClass(ReferenceType refType);
  }
  
  public FieldEvaluator(Evaluator objectEvaluator, TargetClassFilter filter, @NonNls String fieldName) {
    myObjectEvaluator = objectEvaluator;
    myFieldName = fieldName;
    myTargetClassFilter = filter;
  }

  @NotNull
  public static TargetClassFilter createClassFilter(@Nullable PsiType psiType) {
    if(psiType == null || psiType instanceof PsiArrayType) {
      return TargetClassFilter.ALL;
    }
    PsiClass psiClass = PsiUtil.resolveClassInType(psiType);
    if (psiClass != null) {
      return createClassFilter(psiClass);
    }
    return new FQNameClassFilter(psiType.getCanonicalText());
  }

  public static TargetClassFilter createClassFilter(PsiClass psiClass) {
    if (psiClass instanceof PsiAnonymousClass) {
      return TargetClassFilter.ALL;
    }
    if (PsiUtil.isLocalClass(psiClass)) {
      return new LocalClassFilter(psiClass.getName());
    }
    final String name = JVMNameUtil.getNonAnonymousClassName(psiClass);
    return name != null? new FQNameClassFilter(name) : TargetClassFilter.ALL;
  }

  @Nullable
  private Field findField(@Nullable Type t) {
    if(t instanceof ClassType) {
      ClassType cls = (ClassType) t;
      if(myTargetClassFilter.acceptClass(cls)) {
        return cls.fieldByName(myFieldName);
      }
      for (final InterfaceType interfaceType : cls.interfaces()) {
        final Field field = findField(interfaceType);
        if (field != null) {
          return field;
        }
      }
      return findField(cls.superclass());
    }
    else if(t instanceof InterfaceType) {
      InterfaceType iface = (InterfaceType) t;
      if(myTargetClassFilter.acceptClass(iface)) {
        return iface.fieldByName(myFieldName);
      }
      for (final InterfaceType interfaceType : iface.superinterfaces()) {
        final Field field = findField(interfaceType);
        if (field != null) {
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
      Field field = findField(refType);
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
        return context.getDebugProcess().getVirtualMachineProxy().mirrorOf(((ArrayReference)objRef).length());
      }

      Field field = findField(refType);
      if (field == null) {
        field = refType.fieldByName(myFieldName);
      }

      if (field == null) {
        throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.no.instance.field", myFieldName));
      }
      myEvaluatedQualifier = field.isStatic() ? refType : objRef;
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

  @Override
  public String toString() {
    return "field " + myFieldName;
  }

  private static final class FQNameClassFilter implements TargetClassFilter {
    private final String myQName;

    private FQNameClassFilter(String qName) {
      myQName = qName;
    }

    public boolean acceptClass(final ReferenceType refType) {
      return refType.name().equals(myQName);
    }
  }

  private static final class LocalClassFilter implements TargetClassFilter{
    private final String myLocalClassShortName;

    private LocalClassFilter(String localClassShortName) {
      myLocalClassShortName = localClassShortName;
    }

    public boolean acceptClass(final ReferenceType refType) {
      final String name = refType.name();
      final int index = name.lastIndexOf(myLocalClassShortName);
      if (index < 0) {
        return false;
      }
      for (int idx = index - 1; idx >= 0; idx--) {
        final char ch = name.charAt(idx);
        if (ch == '$') {
          return idx < (index - 1);
        }
        if (!Character.isDigit(ch)) {
          return false;
        }
      }
      return false;
    }
  }
}
