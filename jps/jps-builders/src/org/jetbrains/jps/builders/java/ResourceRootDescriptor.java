package org.jetbrains.jps.builders.java;

import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildRootDescriptor;
import org.jetbrains.jps.cmdline.ProjectDescriptor;
import org.jetbrains.jps.incremental.ResourcesTarget;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.java.compiler.JpsCompilerExcludes;

import java.io.File;
import java.io.FileFilter;
import java.util.Set;

/**
* @author Eugene Zhuravlev
*         Date: 1/3/12
*/
public final class ResourceRootDescriptor extends BuildRootDescriptor {

  @NotNull private final File myRoot;
  @NotNull private final ResourcesTarget myTarget;
  private final boolean myGenerated;
  @NotNull private final String myPackagePrefix;
  @NotNull private final Set<File> myExcludes;

  public ResourceRootDescriptor(@NotNull File root, @NotNull ResourcesTarget target, boolean isGenerated, @NotNull String packagePrefix, @NotNull Set<File> excludes) {
    myRoot = root;
    myTarget = target;
    myGenerated = isGenerated;
    myPackagePrefix = packagePrefix;
    myExcludes = excludes;
  }

  @Override
  public String getRootId() {
    return FileUtil.toSystemIndependentName(myRoot.getPath());
  }

  @Override
  public File getRootFile() {
    return myRoot;
  }

  @NotNull
  @Override
  public Set<File> getExcludedRoots() {
    return myExcludes;
  }

  @Override
  public ResourcesTarget getTarget() {
    return myTarget;
  }

  @NotNull
  public String getPackagePrefix() {
    return myPackagePrefix;
  }

  @Override
  public FileFilter createFileFilter(@NotNull ProjectDescriptor descriptor) {
    final JpsProject project = myTarget.getModule().getProject();
    final JpsCompilerExcludes excludes = JpsJavaExtensionService.getInstance().getOrCreateCompilerConfiguration(project).getCompilerExcludes();
    return new FileFilter() {
      @Override
      public boolean accept(File file) {
        return !excludes.isExcluded(file);
      }
    };
  }

  @Override
  public boolean isGenerated() {
    return myGenerated;
  }

  @Override
  public String toString() {
    return "ResourceRootDescriptor{" +
           "target='" + myTarget + '\'' +
           ", root=" + myRoot +
           ", generated=" + myGenerated +
           '}';
  }
}
