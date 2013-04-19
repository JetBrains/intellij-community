package com.intellij.openapi.externalSystem.model.project.id;

import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.ProjectEntityType;
import com.intellij.openapi.externalSystem.service.project.ProjectStructureServices;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Whole 'entity id' thing is necessary for mapping 'sync project structures' tree nodes to the domain entities. However, there are
 * special types of nodes that don't map to any entity - {@link ProjectEntityType#SYNTHETIC synthetic nodes}.
 * <p/>
 * This class is designed to serve as a data object for such a nodes.
 * <p/>
 * Thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 2/16/12 11:40 AM
 */
public class GradleSyntheticId extends AbstractExternalEntityId {
  
  @NotNull private final String myText;

  public GradleSyntheticId(@NotNull String text) {
    super(ProjectEntityType.SYNTHETIC, ProjectSystemId.IDE/* no matter what owner is used */);
    myText = text;
  }

  @Override
  public String toString() {
    return myText;
  }

  @Nullable
  @Override
  public Object mapToEntity(@NotNull ProjectStructureServices services, @NotNull Project ideProject) {
    return null;
  }
}
