package com.intellij.openapi.externalSystem.model.project;

import com.intellij.openapi.roots.DependencyScope;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.ObjectInputStream;

/**
 * @author Denis Zhdanov
 * @since 8/10/11 6:41 PM
 */
public abstract class AbstractDependencyData<T extends AbstractExternalEntityData & Named> extends AbstractExternalEntityData
  implements DependencyData, Named, OrderAware
{

  private static final long serialVersionUID = 1L;

  @NotNull private final ModuleData myOwnerModule;
  @NotNull private final T          myTarget;

  private DependencyScope myScope = DependencyScope.COMPILE;

  private boolean myExported;
  private int myOrder;

  protected AbstractDependencyData(@NotNull ModuleData ownerModule, @NotNull T dependency) {
    super(ownerModule.getOwner());
    myOwnerModule = ownerModule;
    myTarget = dependency;
  }

  @NotNull
  public ModuleData getOwnerModule() {
    return myOwnerModule;
  }

  @NotNull
  public T getTarget() {
    return myTarget;
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

  @SuppressWarnings("MethodOverridesPrivateMethodOfSuperclass")
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
