package org.jetbrains.jps.incremental;

import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.java.JavaModuleBuildTargetType;
import org.jetbrains.jps.incremental.artifacts.ArtifactRootsIndex;
import org.jetbrains.jps.incremental.fs.RootDescriptor;
import org.jetbrains.jps.model.java.JpsJavaDependenciesEnumerator;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.module.JpsModule;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author nik
 */
public class ModuleBuildTarget extends BuildTarget {
  private final JpsModule myModule;
  private final String myModuleName;
  private final JavaModuleBuildTargetType myTargetType;

  public ModuleBuildTarget(@NotNull JpsModule module, JavaModuleBuildTargetType targetType) {
    super(targetType);
    myTargetType = targetType;
    myModuleName = module.getName();
    myModule = module;
  }

  @NotNull
  public JpsModule getModule() {
    return myModule;
  }

  public String getModuleName() {
    return myModuleName;
  }

  public boolean isTests() {
    return myTargetType.isTests();
  }

  @Override
  public String getId() {
    return myModuleName;
  }

  @Override
  public Collection<ModuleBuildTarget> computeDependencies() {
    JpsJavaDependenciesEnumerator enumerator = JpsJavaExtensionService.dependencies(myModule).compileOnly();
    if (!isTests()) {
      enumerator.productionOnly();
    }
    final List<ModuleBuildTarget> dependencies = new ArrayList<ModuleBuildTarget>();
    enumerator.processModules(new Consumer<JpsModule>() {
      @Override
      public void consume(JpsModule module) {
        dependencies.add(new ModuleBuildTarget(module, myTargetType));
      }
    });
    if (isTests()) {
      dependencies.add(new ModuleBuildTarget(myModule, JavaModuleBuildTargetType.PRODUCTION));
    }
    return dependencies;
  }

  @Override
  public RootDescriptor findRootDescriptor(String rootId, ModuleRootsIndex index, ArtifactRootsIndex artifactRootsIndex) {
    return index.getRootDescriptor(null, new File(rootId));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || !(o instanceof ModuleBuildTarget)) {
      return false;
    }

    ModuleBuildTarget target = (ModuleBuildTarget)o;
    return myTargetType == target.myTargetType && myModuleName.equals(target.myModuleName);
  }

  @Override
  public int hashCode() {
    return 31 * myModuleName.hashCode() + myTargetType.hashCode();
  }
}
