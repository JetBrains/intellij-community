package com.intellij.openapi.externalSystem.model.project;

import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.id.JarId;
import com.intellij.openapi.externalSystem.model.project.id.LibraryId;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.roots.libraries.Library;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Denis Zhdanov
 * @since 12/11/12 3:07 PM
 */
public class Jar extends AbstractNamedExternalEntity {

  @NotNull private final String myPath;

  @Nullable private final Library         myIdeLibrary;
  @Nullable private final ExternalLibrary myExternalLibrary;
  @NotNull private final  LibraryPathType myPathType;
  @Nullable private final ProjectSystemId myExternalSystemId;

  public Jar(@NotNull String path,
             @NotNull LibraryPathType pathType,
             @Nullable Library ideLibrary,
             @Nullable ExternalLibrary externalLibrary,
             @Nullable ProjectSystemId owner)
  {
    super(ExternalSystemUtil.detectOwner(externalLibrary, ideLibrary), ExternalSystemUtil.extractNameFromPath(path));
    myPathType = pathType;
    myExternalSystemId = owner;
    assert ideLibrary == null ^ externalLibrary == null;
    myPath = path;
    myIdeLibrary = ideLibrary;
    myExternalLibrary = externalLibrary;
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
    assert myExternalLibrary != null;
    return new LibraryId(myExternalSystemId, myExternalLibrary.getName());
  }

  @Override
  public void invite(@NotNull ExternalEntityVisitor visitor) {
    visitor.visit(this);
  }

  @NotNull
  @Override
  public Jar clone(@NotNull ExternalEntityCloneContext context) {
    ExternalLibrary library = myExternalLibrary == null ? null : context.getLibrary(myExternalLibrary);
    return new Jar(myPath, myPathType, myIdeLibrary, library, myExternalSystemId);
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + myPath.hashCode();
    result = 31 * result + myPathType.hashCode();
    result = 31 * result + (myIdeLibrary != null ? myIdeLibrary.hashCode() : 0);
    result = 31 * result + (myExternalLibrary != null ? myExternalLibrary.hashCode() : 0);
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (!super.equals(o)) return false;

    Jar that = (Jar)o;
    
    if (!myPath.equals(that.myPath)) return false;
    if (!myPathType.equals(that.myPathType)) return false;
    if (myExternalLibrary != null ? !myExternalLibrary.equals(that.myExternalLibrary) : that.myExternalLibrary != null) return false;
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
      myIdeLibrary == null ? myExternalLibrary.getName() : ExternalSystemUtil.getLibraryName(myIdeLibrary)
    );
  }
}
