// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.builders.java.dependencyView;

import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene Zhuravlev
 */
class DifferenceImpl extends Difference{

  private final Difference myDelegate;

  public DifferenceImpl(@NotNull Difference delegate) {
    myDelegate = delegate;
  }

  public int base() {
    return myDelegate.base();
  }

  public boolean no() {
    return myDelegate.no();
  }

  public boolean accessRestricted() {
    return myDelegate.accessRestricted();
  }

  public int addedModifiers() {
    return myDelegate.addedModifiers();
  }

  public int removedModifiers() {
    return myDelegate.removedModifiers();
  }

  public boolean packageLocalOn() {
    return myDelegate.packageLocalOn();
  }

  public boolean hadValue() {
    return myDelegate.hadValue();
  }

  public Specifier<TypeRepr.ClassType, Difference> annotations() {
    return myDelegate.annotations();
  }
}
