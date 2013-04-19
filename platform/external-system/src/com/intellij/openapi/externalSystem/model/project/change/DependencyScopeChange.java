package com.intellij.openapi.externalSystem.model.project.change;

import com.intellij.openapi.externalSystem.model.project.id.AbstractExternalDependencyId;
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
import com.intellij.openapi.roots.DependencyScope;
import org.jetbrains.annotations.NotNull;

/**
 * @author Denis Zhdanov
 * @since 3/12/12 6:02 PM
 */
public class DependencyScopeChange extends AbstractConflictingPropertyChange<DependencyScope> {
  
  public DependencyScopeChange(@NotNull AbstractExternalDependencyId id,
                               @NotNull DependencyScope gradleValue,
                               @NotNull DependencyScope intellijValue)
  {
    super(id, ExternalSystemBundle.message("change.dependency.scope", id), gradleValue, intellijValue);
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
