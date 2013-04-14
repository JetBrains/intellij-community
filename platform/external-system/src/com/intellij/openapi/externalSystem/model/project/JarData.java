package com.intellij.openapi.externalSystem.model.project;

import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.id.JarId;
import com.intellij.openapi.externalSystem.model.project.id.LibraryId;
import com.intellij.openapi.externalSystem.model.project.id.ProjectEntityId;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.roots.libraries.Library;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Denis Zhdanov
 * @since 12/11/12 3:07 PM
 */
public class JarData extends AbstractNamedData {

  @NotNull private final String myPath;

  @Nullable private final Library         myIdeLibrary;
  @Nullable private final LibraryData     myLibraryData;
  @NotNull private final  LibraryPathType myPathType;
  @Nullable private final ProjectSystemId myExternalSystemId;

  public JarData(@NotNull String path,
                 @NotNull LibraryPathType pathType,
                 @Nullable Library ideLibrary,
                 @Nullable LibraryData libraryData,
                 @Nullable ProjectSystemId owner)
  {
    super(ExternalSystemUtil.detectOwner(libraryData, ideLibrary), ExternalSystemUtil.extractNameFromPath(path));
    myPathType = pathType;
    myExternalSystemId = owner;
    assert ideLibrary == null ^ libraryData == null;
    myPath = path;
    myIdeLibrary = ideLibrary;
    myLibraryData = libraryData;
  }

  @NotNull
  @Override
  public ProjectEntityId getId(@Nullable DataNode<?> dataNode) {
    return getId();
  }

  @NotNull
  public String getPath() {
    return myPath;
  }

  @NotNull
  public LibraryPathType getPathType() {
    return myPathType;
  }

  @NotNull
  public JarId getId() {
    return new JarId(myPath, myPathType, getLibraryId());
  }
  
  @NotNull
  public LibraryId getLibraryId() {
    if (myIdeLibrary != null) {
      return new LibraryId(ProjectSystemId.IDE, ExternalSystemUtil.getLibraryName(myIdeLibrary));
    }
    assert myExternalSystemId != null;
    assert myLibraryData != null;
    return new LibraryId(myExternalSystemId, myLibraryData.getName());
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + myPath.hashCode();
    result = 31 * result + myPathType.hashCode();
    result = 31 * result + (myIdeLibrary != null ? myIdeLibrary.hashCode() : 0);
    result = 31 * result + (myLibraryData != null ? myLibraryData.hashCode() : 0);
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (!super.equals(o)) return false;

    JarData that = (JarData)o;
    
    if (!myPath.equals(that.myPath)) return false;
    if (!myPathType.equals(that.myPathType)) return false;
    if (myLibraryData != null ? !myLibraryData.equals(that.myLibraryData) : that.myLibraryData != null) return false;
    if (myIdeLibrary == null && that.myIdeLibrary != null) {
      return false;
    }
    else if (myIdeLibrary != null) {
      if (that.myIdeLibrary == null) {
        return false;
      }
      else if (!ExternalSystemUtil.getLibraryName(myIdeLibrary).equals(ExternalSystemUtil.getLibraryName(that.myIdeLibrary))) {
        return false;
      }
    }
    return true;
  }

  @SuppressWarnings("ConstantConditions")
  @Override
  public String toString() {
    return String.format(
      "%s jar at '%s'. Belongs to library '%s'",
      myPathType.toString().toLowerCase(), myPath,
      myIdeLibrary == null ? myLibraryData.getName() : ExternalSystemUtil.getLibraryName(myIdeLibrary)
    );
  }
}
