package com.intellij.openapi.externalSystem.model.project.change;

import com.intellij.openapi.externalSystem.model.project.id.JarId;
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Denis Zhdanov
 * @since 12/11/12 7:52 PM
 */
public class JarPresenceChange extends AbstractProjectEntityPresenceChange<JarId> {

  public JarPresenceChange(@Nullable JarId gradleEntity, @Nullable JarId ideEntity) {
    super(ExternalSystemBundle.message("entity.type.jar"), gradleEntity, ideEntity);
  }

  @Override
  public void invite(@NotNull ExternalProjectStructureChangeVisitor visitor) {
    visitor.visit(this); 
  }
}
