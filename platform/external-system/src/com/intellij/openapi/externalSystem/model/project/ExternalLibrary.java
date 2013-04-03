package com.intellij.openapi.externalSystem.model.project;

import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Not thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 8/24/11 4:50 PM
 */
public class ExternalLibrary extends AbstractNamedExternalEntity implements Named {

  private static final long serialVersionUID = 1L;

  private final Map<LibraryPathType, Set<String>> myPaths = new HashMap<LibraryPathType, Set<String>>();

  public ExternalLibrary(@NotNull ProjectSystemId owner, @NotNull String name) {
    super(owner, name);
  }

  @NotNull
  public Set<String> getPaths(@NotNull LibraryPathType type) {
    Set<String> result = myPaths.get(type);
    return result == null ? Collections.<String>emptySet() : result;
  }

  public void addPath(@NotNull LibraryPathType type, @NotNull String path) {
    Set<String> paths = myPaths.get(type);
    if (paths == null) {
      myPaths.put(type, paths = new HashSet<String>());
    } 
    paths.add(ExternalSystemUtil.toCanonicalPath(path));
  }

  public void forgetAllPaths() {
    myPaths.clear();
  }
  
  @Override
  public void invite(@NotNull ExternalEntityVisitor visitor) {
    visitor.visit(this);
  }

  @Override
  public int hashCode() {
    int result = myPaths.hashCode();
    result = 31 * result + super.hashCode();
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (!super.equals(o)) return false;

    ExternalLibrary that = (ExternalLibrary)o;
    return super.equals(that) && myPaths.equals(that.myPaths);
  }

  @Override
  public String toString() {
    return "library " + getName();
  }

  @NotNull
  @Override
  public ExternalLibrary clone(@NotNull ExternalEntityCloneContext context) {
    ExternalLibrary result = context.getLibrary(this);
    if (result == null) {
      result = new ExternalLibrary(getOwner(), getName());
      context.store(this, result);
      for (Map.Entry<LibraryPathType, Set<String>> entry : myPaths.entrySet()) {
        for (String path : entry.getValue()) {
          result.addPath(entry.getKey(), path);
        }
      }
    } 
    return result;
  }
}
