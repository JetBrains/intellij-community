package com.intellij.openapi.externalSystem.model.project;

import com.intellij.ide.highlighter.ModuleFileType;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.util.containers.ContainerUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Module settings holder.
 * <p/>
 * <b>Note:</b> doesn't consider {@link ExternalLibraryDependency library} {@link #getDependencies() dependencies}
 * at {@link #equals(Object)}/{@link #hashCode()}.
 * 
 * @author Denis Zhdanov
 * @since 8/8/11 12:11 PM
 */
public class ExternalModule extends AbstractNamedExternalEntity implements Named {

  private static final long serialVersionUID = 1L;

  private final List<ExternalContentRoot> myContentRoots       = ContainerUtilRt.newArrayList();
  private final Map<SourceType, String> myCompileOutputPaths = ContainerUtilRt.newHashMap();
  private final Set<ExternalDependency>   myDependencies       = ContainerUtilRt.newHashSet();

  private String myModuleFilePath;
  private boolean myInheritProjectCompileOutputPath = true;

  public ExternalModule(@NotNull ProjectSystemId owner, @NotNull String name, @NotNull String moduleFileDirectoryPath) {
    super(owner, name);
    setModuleFileDirectoryPath(moduleFileDirectoryPath);
  }

  public String getModuleFilePath() {
    return myModuleFilePath;
  }

  public void setModuleFileDirectoryPath(@NotNull String path) {
    myModuleFilePath = ExternalSystemUtil.toCanonicalPath(path + "/" + getName() + ModuleFileType.DOT_DEFAULT_EXTENSION);
  }

  @NotNull
  public Collection<ExternalContentRoot> getContentRoots() {
    return myContentRoots;
  }

  public void addContentRoot(@NotNull ExternalContentRoot contentRoot) {
    myContentRoots.add(contentRoot);
  }

  public void clearContentRoots() {
    myContentRoots.clear();
  }
  
  public boolean isInheritProjectCompileOutputPath() {
    return myInheritProjectCompileOutputPath;
  }

  public void setInheritProjectCompileOutputPath(boolean inheritProjectCompileOutputPath) {
    myInheritProjectCompileOutputPath = inheritProjectCompileOutputPath;
  }

  /**
   * Allows to get file system path of the compile output of the source of the target type.
   *
   * @param type  target source type
   * @return      file system path to use for compile output for the target source type;
   *              {@link ExternalProject#getCompileOutputPath() project compile output path} should be used if current module
   *              doesn't provide specific compile output path
   */
  @Nullable
  public String getCompileOutputPath(@NotNull SourceType type) {
    return myCompileOutputPaths.get(type);
  }

  public void setCompileOutputPath(@NotNull SourceType type, @Nullable String path) {
    if (path == null) {
      myCompileOutputPaths.remove(type);
      return;
    }
    myCompileOutputPaths.put(type, ExternalSystemUtil.toCanonicalPath(path));
  }

  @NotNull
  public Collection<ExternalDependency> getDependencies() {
    return myDependencies;
  }

  public void addDependency(@NotNull ExternalDependency dependency) {
    myDependencies.add(dependency);
  }

  public void clearDependencies() {
    myDependencies.clear();
  }
  
  @Override
  public void invite(@NotNull ExternalEntityVisitor visitor) {
    visitor.visit(this);
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    
    // Sometimes instance fields are not initialised here during deserialization. Seems to be standard library bug. That's why we
    // do null-check here.
    result = myModuleFilePath == null ? result : (31 * result + myModuleFilePath.hashCode());
    result = 31 * result + (myInheritProjectCompileOutputPath ? 1 : 0);
    result = myCompileOutputPaths == null ? result : (31 * result + myCompileOutputPaths.hashCode());
    result = myContentRoots == null ? result : (31 * result + myContentRoots.hashCode());
    
    // We intentionally don't use dependencies here in order to allow module mappings before and after external libraries
    // resolving (downloading)
    
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (!super.equals(o)) return false;

    ExternalModule that = (ExternalModule)o;

    if (!super.equals(that)) return false;
    // Sometimes instance fields are not initialised here during deserialization. Seems to be standard library bug. That's why we
    // do null-check here.
    if (myModuleFilePath == null ? that.myModuleFilePath != null : !myModuleFilePath.equals(that.myModuleFilePath)) return false;
    if (myInheritProjectCompileOutputPath != that.myInheritProjectCompileOutputPath) return false;
    if (myCompileOutputPaths == null
        ? that.myCompileOutputPaths != null : !myCompileOutputPaths.equals(that.myCompileOutputPaths)) return false;
    if (myContentRoots == null ? that.myContentRoots != null : !myContentRoots.equals(that.myContentRoots)) return false;
    
    // We intentionally don't use dependencies here in order to allow module mappings before and after external libraries
    // resolving (downloading)
    
    return true;
  }

  @Override
  public String toString() {
    return String.format("module '%s'", getName());
  }

  @NotNull
  @Override
  public ExternalModule clone(@NotNull ExternalEntityCloneContext context) {
    ExternalModule result = context.getModule(this);
    if (result != null) {
      return result;
    }
    result = new ExternalModule(getOwner(), getName(), new File(getModuleFilePath()).getParent());
    context.store(this, result);
    result.setInheritProjectCompileOutputPath(isInheritProjectCompileOutputPath());
    for (ExternalContentRoot contentRoot : getContentRoots()) {
      result.addContentRoot(contentRoot.clone(context));
    }
    for (Map.Entry<SourceType, String> entry : myCompileOutputPaths.entrySet()) {
      result.setCompileOutputPath(entry.getKey(), entry.getValue());
    }
    for (ExternalDependency dependency : getDependencies()) {
      result.addDependency(dependency.clone(context));
    }
    return result;
  }
}
