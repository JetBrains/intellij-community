package org.jetbrains.jps.builders;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.module.JpsModule;

/**
 * @author Eugene Zhuravlev
 *         Date: 10/27/12
 */
public abstract class ModuleBasedTarget<R extends BuildRootDescriptor> extends BuildTarget<R> {
  protected final JpsModule myModule;

  public ModuleBasedTarget(BuildTargetType<?> targetType, @NotNull JpsModule module) {
    super(targetType);
    myModule = module;
  }

  @NotNull
  public JpsModule getModule() {
    return myModule;
  }

  public boolean isCompiledBeforeModuleLevelBuilders() {
    return false;
  }

  public abstract boolean isTests();

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || !(o instanceof ModuleBasedTarget)) {
      return false;
    }

    ModuleBasedTarget target = (ModuleBasedTarget)o;
    return getTargetType() == target.getTargetType() && getId().equals(target.getId());
  }

  @Override
  public int hashCode() {
    return 31 * getId().hashCode() + getTargetType().hashCode();
  }

}
