package org.jetbrains.jps.builders.java;

import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildRootDescriptor;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.incremental.ModuleBuildTarget;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.java.compiler.JpsCompilerExcludes;

import java.io.File;
import java.io.FileFilter;

/**
* @author Eugene Zhuravlev
*         Date: 1/3/12
*/
public final class JavaSourceRootDescriptor extends BuildRootDescriptor {
  @NotNull
  public final File root;
  @NotNull
  public final ModuleBuildTarget target;
  public final boolean isGeneratedSources;
  public final boolean isTemp;
  private final String myPackagePrefix;

  public JavaSourceRootDescriptor(@NotNull File root, @NotNull ModuleBuildTarget target, boolean isGenerated, boolean isTemp, @NotNull String packagePrefix) {
    this.root = root;
    this.target = target;
    this.isGeneratedSources = isGenerated;
    this.isTemp = isTemp;
    myPackagePrefix = packagePrefix;
  }

  @Override
  public String toString() {
    return "RootDescriptor{" +
           "target='" + target + '\'' +
           ", root=" + root +
           ", generated=" + isGeneratedSources +
           '}';
  }

  @NotNull
  public String getPackagePrefix() {
    return myPackagePrefix;
  }

  @Override
  public String getRootId() {
    return FileUtil.toSystemIndependentName(root.getPath());
  }

  @Override
  public File getRootFile() {
    return root;
  }

  @Override
  public BuildTarget<?> getTarget() {
    return target;
  }

  @Override
  public FileFilter createFileFilter() {
    final JpsCompilerExcludes excludes = JpsJavaExtensionService.getInstance().getOrCreateCompilerConfiguration(target.getModule().getProject()).getCompilerExcludes();
    return new FileFilter() {
      @Override
      public boolean accept(File file) {
        return !excludes.isExcluded(file);
      }
    };
  }

  @Override
  public boolean isGenerated() {
    return isGeneratedSources;
  }
}
