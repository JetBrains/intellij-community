package com.intellij.openapi.externalSystem.model.project;

import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Not thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 8/24/11 4:50 PM
 */
public class LibraryData extends AbstractNamedData implements Named, ProjectCoordinate {

  private static final long serialVersionUID = 1L;

  private final Map<LibraryPathType, Set<String>> myPaths = new HashMap<>();
  
  private final boolean myUnresolved;
  private String myGroup;
  private String myArtifactId;
  private String myVersion;

  public LibraryData(@NotNull ProjectSystemId owner, @NotNull String name) {
    this(owner, name, false);
  }

  public LibraryData(@NotNull ProjectSystemId owner, @NotNull String name, boolean unresolved) {
    super(owner, name, name.isEmpty() ? "" : owner.getReadableName() + ": " + name);
    myUnresolved = unresolved;
  }

  @Nullable
  @Override
  public String getGroupId() {
    return myGroup;
  }

  public void setGroup(String group) {
    myGroup = group;
  }

  @Nullable
  @Override
  public String getArtifactId() {
    return myArtifactId;
  }

  public void setArtifactId(String artifactId) {
    myArtifactId = artifactId;
  }

  @Nullable
  @Override
  public String getVersion() {
    return myVersion;
  }

  public void setVersion(String version) {
    myVersion = version;
  }

  public boolean isUnresolved() {
    return myUnresolved;
  }

  @NotNull
  public Set<String> getPaths(@NotNull LibraryPathType type) {
    Set<String> result = myPaths.get(type);
    return result == null ? Collections.emptySet() : result;
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
    result = 31 * result + (myGroup != null ? myGroup.hashCode() : 0);
    result = 31 * result + (myArtifactId != null ? myArtifactId.hashCode() : 0);
    result = 31 * result + (myVersion != null ? myVersion.hashCode() : 0);
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (!super.equals(o)) return false;

    LibraryData that = (LibraryData)o;
    if (myGroup != null ? !myGroup.equals(that.myGroup) : that.myGroup != null) return false;
    if (myArtifactId != null ? !myArtifactId.equals(that.myArtifactId) : that.myArtifactId != null) return false;
    if (myVersion != null ? !myVersion.equals(that.myVersion) : that.myVersion != null) return false;
    return super.equals(that) && myUnresolved == that.myUnresolved && myPaths.equals(that.myPaths);
  }

  @Override
  public String toString() {
    String externalName = getExternalName();
    String displayName = StringUtil.isEmpty(externalName) ? myPaths.toString() : externalName;
    return String.format("library %s%s", displayName, myUnresolved ? "(unresolved)" : "");
  }
}
