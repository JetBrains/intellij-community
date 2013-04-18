/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.externalSystem.model.project.change;

import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.id.EntityIdMapper;
import com.intellij.openapi.externalSystem.model.project.id.ModuleId;
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Denis Zhdanov
 * @since 11/17/11 12:50 PM
 */
public class ModulePresenceChange extends AbstractProjectEntityPresenceChange<ModuleId> {

  public ModulePresenceChange(@Nullable ModuleData externalModule, @Nullable Module ideModule)
    throws IllegalArgumentException
  {
    super(ExternalSystemBundle.message("entity.type.module"), of(externalModule), of(ideModule));
  }

  @Override
  public void invite(@NotNull ExternalProjectStructureChangeVisitor visitor) {
    visitor.visit(this);
  }

  @Nullable
  private static ModuleId of(@Nullable Object module) {
    if (module == null) {
      return null;
    }
    return EntityIdMapper.mapEntityToId(module);
  }
}
