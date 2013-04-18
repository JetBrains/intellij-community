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

import com.intellij.openapi.externalSystem.model.project.id.ProjectEntityId;
import org.jetbrains.annotations.NotNull;

/**
 * Defines general contract for a change that encapsulates information about conflicting property value of the matched external system
 * and ide entities.
 * <p/>
 * For example we may match particular external system library to an ide library but they may have different set of attached binaries.
 * <p/>
 * Thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 11/15/11 7:57 PM
 * @param <T>   target property value type
 */
public abstract class AbstractConflictingPropertyChange<T> extends AbstractExternalProjectStructureChange {

  @NotNull private final ProjectEntityId myEntityId;
  @NotNull private final String          myPropertyDescription;
  @NotNull private final T               myExternalValue;
  @NotNull private final T               myIdeValue;

  public AbstractConflictingPropertyChange(@NotNull ProjectEntityId id,
                                           @NotNull String propertyDescription,
                                           @NotNull T externalValue,
                                           @NotNull T ideValue)
  {
    myEntityId = id;
    myPropertyDescription = propertyDescription;
    myExternalValue = externalValue;
    myIdeValue = ideValue;
  }

  @NotNull
  public ProjectEntityId getEntityId() {
    return myEntityId;
  }

  /**
   * @return target property's value at external system side
   */
  @NotNull
  public T getExternalValue() {
    return myExternalValue;
  }

  /**
   * @return target property's value at ide side
   */
  @NotNull
  public T getIdeValue() {
    return myIdeValue;
  }

  @Override
  public int hashCode() {
    int result = 31 * super.hashCode() + myEntityId.hashCode();
    result = 31 * result + myExternalValue.hashCode();
    return 31 * result + myIdeValue.hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    AbstractConflictingPropertyChange that = (AbstractConflictingPropertyChange)o;

    return myEntityId.equals(that.myEntityId) && myExternalValue.equals(that.myExternalValue) && myIdeValue.equals(that.myIdeValue);
  }

  @Override
  public String toString() {
    return String.format("%s change: external='%s', ide='%s'", myPropertyDescription, myExternalValue, myIdeValue);
  }
}
