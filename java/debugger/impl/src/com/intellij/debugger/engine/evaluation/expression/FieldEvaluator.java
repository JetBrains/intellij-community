// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * Class FieldEvaluator
 * @author Jeka
 */
package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.engine.DebuggerUtils;
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

public class FieldEvaluator implements ModifiableEvaluator {
  private final Evaluator myObjectEvaluator;
  private final TargetClassFilter myTargetClassFilter;
  private final String myFieldName;

  // TODO remove non-final fields, see IDEA-366793
  @Deprecated
  private Object myEvaluatedQualifier;
  @Deprecated
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

  public static @NotNull TargetClassFilter createClassFilter(@Nullable PsiType psiType) {
    if (psiType == null || psiType instanceof PsiArrayType) {
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
    final String name = JVMNameUtil.getClassVMName(psiClass);
    return name != null ? new FQNameClassFilter(name) : TargetClassFilter.ALL;
  }

  private @Nullable Field findField(@Nullable Type t) {
    if (t instanceof ClassType cls) {
      if (myTargetClassFilter.acceptClass(cls)) {
        return DebuggerUtils.findField(cls, myFieldName);
      }
      for (final InterfaceType interfaceType : cls.interfaces()) {
        final Field field = findField(interfaceType);
        if (field != null) {
          return field;
        }
      }
      return findField(cls.superclass());
    }
    else if (t instanceof InterfaceType iface) {
      if (myTargetClassFilter.acceptClass(iface)) {
        return DebuggerUtils.findField(iface, myFieldName);
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

  @Override
  public @NotNull ModifiableValue evaluateModifiable(EvaluationContextImpl context) throws EvaluateException {
    Object object = myObjectEvaluator.evaluate(context);

    if (object instanceof ReferenceType refType) {
      Field field = findField(refType);
      if (field == null || !field.isStatic()) {
        field = DebuggerUtils.findField(refType, myFieldName);
      }
      if (field == null || !field.isStatic()) {
        throw EvaluateExceptionUtil.createEvaluateException(JavaDebuggerBundle.message("evaluation.error.no.static.field", myFieldName));
      }
      MyModifier modifier = new MyModifier(refType, field);
      myEvaluatedField = field;
      myEvaluatedQualifier = refType;
      return new ModifiableValue(refType.getValue(field), modifier);
    }

    if (object instanceof ObjectReference objRef) {
      ReferenceType refType = objRef.referenceType();
      if (!(refType instanceof ClassType || refType instanceof ArrayType)) {
        throw EvaluateExceptionUtil.createEvaluateException(
          JavaDebuggerBundle.message("evaluation.error.class.or.array.expected", myFieldName));
      }

      // expressions like 'array.length' must be treated separately
      if (objRef instanceof ArrayReference && "length".equals(myFieldName)) {
        return new ModifiableValue(context.getVirtualMachineProxy().mirrorOf(((ArrayReference)objRef).length()), null);
      }

      Field field = findField(refType);
      if (field == null) {
        field = DebuggerUtils.findField(refType, myFieldName);
      }

      if (field == null) {
        throw EvaluateExceptionUtil.createEvaluateException(JavaDebuggerBundle.message("evaluation.error.no.instance.field", myFieldName));
      }
      Object qualifier = field.isStatic() ? refType : objRef;
      MyModifier modifier = new MyModifier(qualifier, field);
      myEvaluatedQualifier = qualifier;
      myEvaluatedField = field;
      return new ModifiableValue(field.isStatic() ? refType.getValue(field) : objRef.getValue(field), modifier);
    }

    if (object == null) {
      throw EvaluateExceptionUtil.createEvaluateException(new NullPointerException());
    }

    throw EvaluateExceptionUtil.createEvaluateException(JavaDebuggerBundle.message("evaluation.error.evaluating.field", myFieldName));
  }

  @Override
  public Modifier getModifier() {
    if (myEvaluatedField != null && (myEvaluatedQualifier instanceof ClassType || myEvaluatedQualifier instanceof ObjectReference)) {
      return new MyModifier(myEvaluatedQualifier, myEvaluatedField);
    }
    return null;
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

    @Override
    public boolean acceptClass(final ReferenceType refType) {
      return refType.name().equals(myQName);
    }
  }

  private static final class LocalClassFilter implements TargetClassFilter {
    private final String myLocalClassShortName;

    private LocalClassFilter(String localClassShortName) {
      myLocalClassShortName = localClassShortName;
    }

    @Override
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

  private static class MyModifier implements Modifier {
    private final Object myEvaluatedQualifier;
    private final Field myEvaluatedField;

    private MyModifier(Object qualifier, Field field) {
      myEvaluatedQualifier = qualifier;
      myEvaluatedField = field; }

    @Override
    public boolean canInspect() {
      return myEvaluatedQualifier instanceof ObjectReference;
    }

    @Override
    public boolean canSetValue() {
      return true;
    }

    @Override
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

    @Override
    public Type getExpectedType() throws ClassNotLoadedException {
      return myEvaluatedField.type();
    }

    @Override
    public NodeDescriptorImpl getInspectItem(Project project) {
      if (myEvaluatedQualifier instanceof ObjectReference) {
        return new FieldDescriptorImpl(project, (ObjectReference)myEvaluatedQualifier, myEvaluatedField);
      }
      else {
        return null;
      }
    }
  }
}
