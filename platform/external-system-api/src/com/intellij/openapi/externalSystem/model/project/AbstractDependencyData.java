// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.model.project;

import com.intellij.openapi.roots.DependencyScope;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.ObjectInputStream;

public abstract class AbstractDependencyData<T extends AbstractExternalEntityData & Named> extends AbstractExternalEntityData
  implements DependencyData<T>, Named, OrderAware {

  private @NotNull ModuleData ownerModule;
  private @NotNull T target;

  private DependencyScope scope = DependencyScope.COMPILE;

  private boolean exported;
  private int order;

  protected AbstractDependencyData(@NotNull ModuleData ownerModule, @NotNull T target) {
    super(ownerModule.getOwner());

    this.ownerModule = ownerModule;
    this.target = target;
  }

  @Override
  public @NotNull ModuleData getOwnerModule() {
    return ownerModule;
  }

  public void setOwnerModule(@NotNull ModuleData ownerModule) {
    this.ownerModule = ownerModule;
  }

  @Override
  public @NotNull T getTarget() {
    return target;
  }

  public void setTarget(@NotNull T target) {
    this.target = target;
  }

  @Override
  public @NotNull DependencyScope getScope() {
    return scope;
  }

  public void setScope(DependencyScope scope) {
    this.scope = scope;
  }

  @Override
  public boolean isExported() {
    return exported;
  }

  public void setExported(boolean exported) {
    this.exported = exported;
  }

  @Override
  public @NotNull String getExternalName() {
    return target.getExternalName();
  }

  @Override
  public void setExternalName(@NotNull String name) {
    target.setExternalName(name);
  }

  @Override
  public @NotNull String getInternalName() {
    return target.getInternalName();
  }

  @Override
  public void setInternalName(@NotNull String name) {
    target.setInternalName(name);
  }


  @Override
  public int getOrder() {
    return order;
  }

  public void setOrder(int order) {
    this.order = order;
  }

  private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
    in.defaultReadObject();
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + scope.hashCode();
    result = 31 * result + ownerModule.hashCode();
    result = 31 * result + target.hashCode();
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (!super.equals(o)) {
      return false;
    }
    AbstractDependencyData<?> that = (AbstractDependencyData<?>)o;
    return scope.equals(that.scope) &&
           ownerModule.equals(that.ownerModule) &&
           target.equals(that.target);
  }

  @Override
  public String toString() {
    return "dependency=" + getTarget() + "|scope=" + getScope() + "|exported=" + isExported() + "|owner=" + getOwnerModule();
  }
}
