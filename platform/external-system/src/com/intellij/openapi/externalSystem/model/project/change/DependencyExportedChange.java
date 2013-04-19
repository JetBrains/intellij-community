package com.intellij.openapi.externalSystem.model.project.change;

import com.intellij.openapi.externalSystem.model.project.id.AbstractExternalDependencyId;
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
import org.jetbrains.annotations.NotNull;

/**
 * @author Denis Zhdanov
 * @since 3/14/12 1:34 PM
 */
public class DependencyExportedChange extends AbstractConflictingPropertyChange<Boolean> {

  public DependencyExportedChange(@NotNull AbstractExternalDependencyId id, boolean gradleValue, boolean intellijValue) {
    super(id, ExternalSystemBundle.message("change.dependency.exported", id), gradleValue, intellijValue);
  }

  @NotNull
  @Override
  public AbstractExternalDependencyId getEntityId() {
    return (AbstractExternalDependencyId)super.getEntityId();
  }

  @Override
  public void invite(@NotNull ExternalProjectStructureChangeVisitor visitor) {
    visitor.visit(this);
  }
}
