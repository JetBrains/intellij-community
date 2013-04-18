/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.externalSystem.model.project;

import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import org.jetbrains.annotations.NotNull;

/**
 * There is a possible case that the same entity (e.g. library or jar) has different versions at external system and ide side.
 * We treat that not as two entities (external system- and ide-local) but as a single composite entity. This class is a base class
 * for such types of entities.
 * <p/>
 * Thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 1/23/13 9:19 AM
 */
public abstract class AbstractCompositeData<E extends ProjectEntityData, I> extends AbstractProjectEntityData {

  @NotNull private final E myExternalEntity;
  @NotNull private final I myIdeEntity;

  public AbstractCompositeData(@NotNull E externalEntity, @NotNull I ideEntity) {
    super(ProjectSystemId.IDE);
    myExternalEntity = externalEntity;
    myIdeEntity = ideEntity;
  }

  @NotNull
  public E getExternalEntity() {
    return myExternalEntity;
  }

  @NotNull
  public I getIdeEntity() {
    return myIdeEntity;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + myExternalEntity.hashCode();
    result = 31 * result + myIdeEntity.hashCode();
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (!super.equals(o)) return false;

    AbstractCompositeData entity = (AbstractCompositeData)o;

    if (!myExternalEntity.equals(entity.myExternalEntity)) return false;
    if (!myIdeEntity.equals(entity.myIdeEntity)) return false;

    return true;
  }
}
