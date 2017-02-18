package com.intellij.openapi.externalSystem.model.project;

import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Not thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 8/24/11 4:50 PM
 */
public class LibraryData extends AbstractNamedData implements Named {

  private static final long serialVersionUID = 1L;

  private final Map<LibraryPathType, Set<String>> myPaths = new HashMap<>();
  
  private final boolean myUnresolved;

  public LibraryData(@NotNull ProjectSystemId owner, @NotNull String name) {
    this(owner, name, false);
  }

  public LibraryData(@NotNull ProjectSystemId owner, @NotNull String name, boolean unresolved) {
    super(owner, name, name.isEmpty() ? "" : String.format("%s: %s", owner.getReadableName(), name));
    myUnresolved = unresolved;
  }

  public boolean isUnresolved() {
    return myUnresolved;
  }

  @NotNull
  public Set<String> getPaths(@NotNull LibraryPathType type) {
    Set<String> result = myPaths.get(type);
    return result == null ? Collections.<String>emptySet() : result;
  }

  public void addPath(@NotNull LibraryPathType type, @NotNull String path) {
    Set<String> paths = myPaths.get(type);
    if (paths == null) {
      myPaths.put(type, paths = new LinkedHashSet<>());
    } 
    paths.add(ExternalSystemApiUtil.toCanonicalPath(path));
  }

  public void forgetAllPaths() {
    myPaths.clear();
  }
  
  @Override
  public int hashCode() {
    int result = myPaths.hashCode();
    result = 31 * result + super.hashCode();
    result = 31 * result + (myUnresolved ? 0 : 1);
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (!super.equals(o)) return false;

    LibraryData that = (LibraryData)o;
    return super.equals(that) && myUnresolved == that.myUnresolved && myPaths.equals(that.myPaths);
  }

  @Override
  public String toString() {
    return String.format("library %s%s", getExternalName(), myUnresolved ? "(unresolved)" : "");
  }
}
