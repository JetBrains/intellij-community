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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Defines common interface for a change that indicates that particular entity has been added/removed at Gradle or IntelliJ IDEA.
 * <p/>
 * Thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 11/17/11 12:43 PM
 * @param <T>   entity type
 */
public abstract class AbstractProjectEntityPresenceChange<T> extends AbstractExternalProjectStructureChange {

  private final T      myExternalEntity;
  private final T      myIdeEntity;
  private final String myEntityName;

  /**
   * Creates new <code>AbstractProjectEntityPresenceChange</code> change.
   *
   * @param externalEntity    target entity at external system side. <code>null</code> as indication that the entity was
   *                          removed at external system side or added at ide side
   * @param ideEntity         target entity at ide side. <code>null</code> as indication that the entity was removed
   *                          at ide side or added at ide side
   * @throws IllegalArgumentException    if both of the given entities are defined or undefined. Expecting this constructor to be
   *                                     called with one <code>null</code> argument and one non-<code>null</code> argument
   */
  public AbstractProjectEntityPresenceChange(@NotNull String entityName,
                                             @Nullable T externalEntity,
                                             @Nullable T ideEntity)
    throws IllegalArgumentException
  {
    if (!(externalEntity == null ^ ideEntity == null)) {
      throw new IllegalArgumentException(String.format(
        "Can't construct %s object. Reason: expected that only gradle or ide entity is null, actual: external='%s'; ide='%s'",
        getClass(), externalEntity, ideEntity
      ));
    }
    myExternalEntity = externalEntity;
    myIdeEntity = ideEntity;
    myEntityName = entityName;
  }

  @Nullable
  public T getExternalEntity() {
    return myExternalEntity;
  }
  
  @Nullable
  public T getIdeEntity() {
    return myIdeEntity;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (myExternalEntity != null ? myExternalEntity.hashCode() : 0);
    return 31 * result + (myIdeEntity != null ? myIdeEntity.hashCode() : 0);
  }

  @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
  @Override
  public boolean equals(Object o) {
    if (!super.equals(o)) return false;

    AbstractProjectEntityPresenceChange that = (AbstractProjectEntityPresenceChange)o;

    if (myExternalEntity != null ? !myExternalEntity.equals(that.myExternalEntity) : that.myExternalEntity != null) return false;
    if (myIdeEntity != null ? !myIdeEntity.equals(that.myIdeEntity) : that.myIdeEntity != null) return false;

    return true;
  }

  @Override
  public String toString() {
    return String.format("%s presence change: external='%s', ide='%s'", myEntityName, myExternalEntity, myIdeEntity);
  }
}
