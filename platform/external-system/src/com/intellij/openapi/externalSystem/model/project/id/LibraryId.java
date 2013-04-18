package com.intellij.openapi.externalSystem.model.project.id;

import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.ProjectEntityType;
import com.intellij.openapi.externalSystem.service.project.ProjectStructureServices;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Denis Zhdanov
 * @since 2/20/12 7:12 PM
 */
public class LibraryId extends AbstractExternalEntityId {

  @NotNull private final String myLibraryName;
  
  public LibraryId(@NotNull ProjectSystemId owner, @NotNull String libraryName) {
    super(ProjectEntityType.LIBRARY, owner);
    myLibraryName = libraryName;
  }

  @NotNull
  public String getLibraryName() {
    return myLibraryName;
  }

  @Nullable
  @Override
  public Object mapToEntity(@NotNull ProjectStructureServices services, @NotNull Project ideProject) {
    return services.getProjectStructureHelper().findLibrary(myLibraryName, getOwner(), ideProject);
  }

  @Override
  public int hashCode() {
    return 31 * super.hashCode() + myLibraryName.hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (!super.equals(o)) {
      return false;
    }
    LibraryId that = (LibraryId)o;
    return myLibraryName.equals(that.myLibraryName);
  }

  @Override
  public String toString() {
    return "library '" + myLibraryName + "'";
  }
}
