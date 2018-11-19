// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.model.project;

import com.intellij.openapi.roots.DependencyScope;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.ObjectInputStream;

/**
 * @author Denis Zhdanov
 */
public abstract class AbstractDependencyData<T extends AbstractExternalEntityData & Named> extends AbstractExternalEntityData
  implements DependencyData, Named, OrderAware
{

  private static final long serialVersionUID = 1L;

  @NotNull private ModuleData myOwnerModule;
  @NotNull private T          myTarget;

  private DependencyScope myScope = DependencyScope.COMPILE;

  private boolean myExported;
  private int myOrder;

  protected AbstractDependencyData(@NotNull ModuleData ownerModule, @NotNull T dependency) {
    super(ownerModule.getOwner());
    myOwnerModule = ownerModule;
    myTarget = dependency;
  }

  @Override
  @NotNull
  public ModuleData getOwnerModule() {
    return myOwnerModule;
  }

  public void setOwnerModule(@NotNull ModuleData ownerModule) {
    myOwnerModule = ownerModule;
  }

  @Override
  @NotNull
  public T getTarget() {
    return myTarget;
  }

  public void setTarget(@NotNull T target) {
    myTarget = target;
  }

  @Override
  @NotNull
  public DependencyScope getScope() {
    return myScope;
  }

  public void setScope(DependencyScope scope) {
    myScope = scope;
  }

  @Override
  public boolean isExported() {
    return myExported;
  }

  public void setExported(boolean exported) {
    myExported = exported;
  }

  /**
   * please use {@link #getExternalName()} or {@link #getInternalName()} instead
   */
  @NotNull
  @Deprecated
  @Override
  public String getName() {
    return myTarget.getName();
  }

  /**
   * please use {@link #setExternalName(String)} or {@link #setInternalName(String)} instead
   */
  @Deprecated
  @Override
  public void setName(@NotNull String name) {
    myTarget.setName(name);
  }

  @NotNull
  @Override
  public String getExternalName() {
    return myTarget.getExternalName();
  }

  @Override
  public void setExternalName(@NotNull String name) {
    myTarget.setExternalName(name);
  }

  @NotNull
  @Override
  public String getInternalName() {
    return myTarget.getInternalName();
  }

  @Override
  public void setInternalName(@NotNull String name) {
    myTarget.setInternalName(name);
  }


  @Override
  public int getOrder() {
    return myOrder;
  }

  public void setOrder(int order) {
    myOrder = order;
  }

  private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
    in.defaultReadObject();
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + myScope.hashCode();
    result = 31 * result + myOwnerModule.hashCode();
    result = 31 * result + myTarget.hashCode();
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (!super.equals(o)) {
      return false;
    }
    AbstractDependencyData<?> that = (AbstractDependencyData<?>)o;
    return myScope.equals(that.myScope) &&
           myOwnerModule.equals(that.myOwnerModule) &&
           myTarget.equals(that.myTarget);
  }

  @Override
  public String toString() {
    return "dependency=" + getTarget() + "|scope=" + getScope() + "|exported=" + isExported() + "|owner=" + getOwnerModule();
  }
}
