package com.intellij.openapi.externalSystem.model.project.change;

import com.intellij.openapi.externalSystem.model.project.ContentRootData;
import com.intellij.openapi.externalSystem.model.project.id.ContentRootId;
import com.intellij.openapi.externalSystem.model.project.id.EntityIdMapper;
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
import com.intellij.openapi.roots.ContentEntry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Denis Zhdanov
 * @since 2/22/12 5:12 PM
 */
public class ContentRootPresenceChange extends AbstractProjectEntityPresenceChange<ContentRootId> {

  public ContentRootPresenceChange(@Nullable ContentRootData gradleEntity, @Nullable ContentEntry intellijEntity)
    throws IllegalArgumentException
  {
    super(ExternalSystemBundle.message("entity.type.content.root"), of(gradleEntity), of(intellijEntity));
  }

  @Nullable
  private static ContentRootId of(@Nullable Object contentRoot) {
    if (contentRoot == null) {
      return null;
    }
    return EntityIdMapper.mapEntityToId(contentRoot);
  }

  @Override
  public void invite(@NotNull ExternalProjectStructureChangeVisitor visitor) {
    visitor.visit(this); 
  }
}
