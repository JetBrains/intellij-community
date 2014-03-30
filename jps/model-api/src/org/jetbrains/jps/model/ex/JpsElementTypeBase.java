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
package org.jetbrains.jps.model.ex;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.JpsElementChildRole;
import org.jetbrains.jps.model.JpsElementType;

/**
 * A base class for all implementations of {@link org.jetbrains.jps.model.JpsElementType}.
 *
 * <p>
 * If elements of your type don't have any specific properties extend {@link JpsElementTypeWithDummyProperties} instead.
 * </p>
 * @author nik
 */
public abstract class JpsElementTypeBase<P extends JpsElement>implements JpsElementType<P> {
  private final JpsElementChildRole<P> myPropertiesRole = new JpsElementChildRole<P>();

  @NotNull
  @Override
  public final JpsElementChildRole<P> getPropertiesRole() {
    return myPropertiesRole;
  }
}
