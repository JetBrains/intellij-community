package org.jetbrains.jps.builders.java;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.incremental.ModuleBuildTarget;

import java.io.File;

/**
* @author Eugene Zhuravlev
*         Date: 1/3/12
*/
public final class ResourcesOnlyRootDescriptor extends JavaSourceRootDescriptor {

  public ResourcesOnlyRootDescriptor(@NotNull File root,
                                     @NotNull ModuleBuildTarget target,
                                     boolean isGenerated,
                                     boolean isTemp,
                                     @NotNull String packagePrefix) {
    super(root, target, isGenerated, isTemp, packagePrefix);
  }

  @Override
  public String toString() {
    return "ResourceRootDescriptor{" +
           "target='" + target + '\'' +
           ", root=" + root +
           ", generated=" + isGeneratedSources +
           '}';
  }
}
