package com.intellij.openapi.externalSystem.model.project.id;

import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.JarData;
import com.intellij.openapi.externalSystem.model.project.LibraryData;
import com.intellij.openapi.externalSystem.model.project.ProjectEntityType;
import com.intellij.openapi.externalSystem.service.project.ProjectStructureHelper;
import com.intellij.openapi.externalSystem.service.project.ProjectStructureServices;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.openapi.externalSystem.model.project.LibraryPathType;

/**
 * @author Denis Zhdanov
 * @since 12/11/12 3:04 PM
 */
public class JarId extends AbstractExternalEntityId {

  @NotNull private final String          myPath;
  @NotNull private final LibraryId       myLibraryId;
  @NotNull private final LibraryPathType myPathType;

  public JarId(@NotNull String path, @NotNull LibraryPathType pathType, @NotNull LibraryId libraryId) {
    super(ProjectEntityType.JAR, libraryId.getOwner());
    myPath = path;
    myPathType = pathType;
    myLibraryId = libraryId;
  }

  @NotNull
  public String getPath() {
    return myPath;
  }

  @NotNull
  public LibraryId getLibraryId() {
    return myLibraryId;
  }

  @NotNull
  public LibraryPathType getLibraryPathType() {
    return myPathType;
  }

  @Nullable
  @Override
  public JarData mapToEntity(@NotNull ProjectStructureServices services, @NotNull Project ideProject) {
    ProjectStructureHelper helper = services.getProjectStructureHelper();
    String libraryName = myLibraryId.getLibraryName();
    OrderRootType jarType = services.getLibraryPathTypeMapper().map(myPathType);
    Library ideLibrary = helper.findIdeLibrary(libraryName, jarType, myPath, ideProject);
    if (ideLibrary != null) {
      return new JarData(myPath, myPathType, ideLibrary, null, ProjectSystemId.IDE);
    }

    DataNode<LibraryData> gradleLibrary = helper.findExternalLibrary(libraryName, myPathType, myPath, getOwner(), ideProject);
    if (gradleLibrary != null) {
      return new JarData(myPath, myPathType, null, gradleLibrary.getData(), getOwner());
    }
    return null;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + myPath.hashCode();
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (!super.equals(o)) return false;

    JarId that = (JarId)o;
    return myPath.equals(that.myPath);
  }

  @Override
  public String toString() {
    return String.format("%s jar '%s'", myPathType.toString().toLowerCase(), myPath);
  }
}
