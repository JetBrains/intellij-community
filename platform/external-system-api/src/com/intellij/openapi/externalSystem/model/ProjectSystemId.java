package com.intellij.openapi.externalSystem.model;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.util.Map;

/**
 * The general idea of 'external system' integration is to provide management facilities for the project structure defined in
 * terms over than IntelliJ (e.g. maven, gradle, eclipse etc).
 * <p/>
 * This class serves as an id of a system which defines project structure, i.e. it might be any external system or the ide itself.
 * 
 * @author Denis Zhdanov
 */
public class ProjectSystemId implements Serializable {

  private static final long serialVersionUID = 1L;
  private static final Map<String, ProjectSystemId> ourExistingIds = ContainerUtil.newConcurrentMap();
  
  @NotNull public static final ProjectSystemId IDE = new ProjectSystemId("IDE");

  @NotNull private final String myId;
  @NotNull private final String myReadableName;

  public ProjectSystemId(@NotNull String id) {
    this(id, StringUtil.capitalize(id.toLowerCase()));
  }

  public ProjectSystemId(@NotNull String id, @NotNull String readableName) {
    myId = id;
    myReadableName = readableName;
    ourExistingIds.put(id, this);
  }

  @Override
  public int hashCode() {
    return myId.hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ProjectSystemId owner = (ProjectSystemId)o;

    return myId.equals(owner.myId);
  }

  @NotNull
  public String getId() {
    return myId;
  }

  @NotNull
  public String getReadableName() {
    return myReadableName;
  }
  
  @Override
  public String toString() {
    return myId;
  }

  @Nullable
  public static ProjectSystemId findById(@NotNull String id) {
    return ourExistingIds.get(id);
  }

  private Object readResolve() {
    ProjectSystemId cached = ourExistingIds.get(myId);
    if (cached != null) {
      return cached;
    } else {
      return this;
    }
  }
}
