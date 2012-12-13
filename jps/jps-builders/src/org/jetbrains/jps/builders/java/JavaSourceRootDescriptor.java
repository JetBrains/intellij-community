package org.jetbrains.jps.builders.java;

import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildRootDescriptor;
import org.jetbrains.jps.cmdline.ProjectDescriptor;
import org.jetbrains.jps.incremental.ModuleBuildTarget;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.java.compiler.JpsCompilerExcludes;

import java.io.File;
import java.io.FileFilter;
import java.util.Set;

/**
* @author Eugene Zhuravlev
*         Date: 1/3/12
*/
public class JavaSourceRootDescriptor extends BuildRootDescriptor {
  @NotNull
  public final File root;
  @NotNull
  public final ModuleBuildTarget target;
  public final boolean isGeneratedSources;
  public final boolean isTemp;
  private final String myPackagePrefix;
  private final Set<File> myExcludes;

  public JavaSourceRootDescriptor(@NotNull File root,
                                  @NotNull ModuleBuildTarget target,
                                  boolean isGenerated,
                                  boolean isTemp,
                                  @NotNull String packagePrefix,
                                  @NotNull Set<File> excludes) {
    this.root = root;
    this.target = target;
    this.isGeneratedSources = isGenerated;
    this.isTemp = isTemp;
    myPackagePrefix = packagePrefix;
    myExcludes = excludes;
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
  @Override
  public Set<File> getExcludedRoots() {
    return myExcludes;
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
  public ModuleBuildTarget getTarget() {
    return target;
  }

  @Override
  public FileFilter createFileFilter(@NotNull ProjectDescriptor descriptor) {
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

  @Override
  public boolean canUseFileCache() {
    return true;
  }
}
