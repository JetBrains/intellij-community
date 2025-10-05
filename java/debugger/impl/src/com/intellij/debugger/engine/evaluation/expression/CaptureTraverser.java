// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.psi.PsiClass;
import com.intellij.psi.util.PsiTreeUtil;
import com.sun.jdi.ClassType;
import com.sun.jdi.Field;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.ReferenceType;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A helper class to find a value of captured outer class "this" object (probably several levels higher from the current class)
 */
public final class CaptureTraverser {
  private static final CaptureTraverser INVALID = new CaptureTraverser(-1);
  private static final CaptureTraverser DIRECT = new CaptureTraverser(0);
  private final int myIterationCount;

  private CaptureTraverser(int iterationCount) {
    myIterationCount = iterationCount;
  }

  /**
   * @return a traverser that traverses one level of nestedness less. An invalid traverser will be returned if
   * this method is called for the direct traverser.
   */
  public CaptureTraverser oneLevelLess() {
    return myIterationCount == -1 ? this : new CaptureTraverser(myIterationCount - 1);
  }

  /**
   * @return true if this traverser is valid
   */
  public boolean isValid() {
    return myIterationCount > -1;
  }

  /**
   * Looks for a captured outer this having inner this
   *
   * @param objRef an inner this object
   * @return a reference to the outer class instance, or null if not found or this traverser is not valid.
   * Direct traverser simply return the argument.
   */
  @Contract("null -> null")
  public @Nullable ObjectReference traverse(@Nullable ObjectReference objRef) {
    if (objRef == null || !isValid()) return null;
    if (myIterationCount <= 0) return objRef;
    ObjectReference thisRef = objRef;
    for (int idx = 0; idx < myIterationCount && thisRef != null; idx++) {
      thisRef = getOuterObject(thisRef);
    }
    return thisRef;
  }

  /**
   * @return a direct traverser that does nothing
   */
  public static @NotNull CaptureTraverser direct() {
    return DIRECT;
  }

  /**
   * Creates a traverser that is capable to find an instance of the targetClass having a reference to the fromClass instance
   *
   * @param targetClass      target (outer) class
   * @param fromClass        source (inner) class
   * @param checkInheritance if true, inheritors of target class are also acceptable
   * @return a traverser capable to find an instance of the targetClass
   */
  public static @NotNull CaptureTraverser create(@Nullable PsiClass targetClass, @Nullable PsiClass fromClass, boolean checkInheritance) {
    if (targetClass == null || fromClass == null) return INVALID;
    int iterationCount = 0;
    while (fromClass != null &&
           !fromClass.equals(targetClass) &&
           (!checkInheritance || !fromClass.isInheritor(targetClass, true))) {
      iterationCount++;
      fromClass = getOuterClass(fromClass);
    }
    return fromClass == null ? INVALID : new CaptureTraverser(iterationCount);
  }

  private static @Nullable PsiClass getOuterClass(PsiClass aClass) {
    return aClass == null ? null : PsiTreeUtil.getContextOfType(aClass, PsiClass.class, true);
  }

  private static @Nullable ObjectReference getOuterObject(ObjectReference objRef) {
    if (objRef == null) {
      return null;
    }
    ReferenceType type = objRef.referenceType();
    while (type != null) {
      for (Field field : type.fields()) {
        String name = field.name();
        if (name != null && name.startsWith("this$") && field.isFinal() && field.isSynthetic() && !field.isStatic()) {
          ObjectReference rv = (ObjectReference)objRef.getValue(field);
          if (rv != null) {
            return rv;
          }
        }
      }
      type = (type instanceof ClassType classType) ? classType.superclass() : null;
    }
    return null;
  }
}
