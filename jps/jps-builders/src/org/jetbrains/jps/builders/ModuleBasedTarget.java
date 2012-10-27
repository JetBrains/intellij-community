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

  public abstract boolean isTests();
}
