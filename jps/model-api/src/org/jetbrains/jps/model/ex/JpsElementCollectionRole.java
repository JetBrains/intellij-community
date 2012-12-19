/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import org.jetbrains.jps.model.*;

/**
 * @author nik
 */
public class JpsElementCollectionRole<E extends JpsElement> extends JpsElementChildRoleBase<JpsElementCollection<E>>
                                                            implements JpsElementCreator<JpsElementCollection<E>> {
  private final JpsElementChildRole<E> myChildRole;

  private JpsElementCollectionRole(@NotNull JpsElementChildRole<E> role) {
    super("collection of " + role);
    myChildRole = role;
  }

  @NotNull
  @Override
  public JpsElementCollection<E> create() {
    return JpsExElementFactory.getInstance().createCollection(myChildRole);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    return myChildRole.equals(((JpsElementCollectionRole)o).myChildRole);
  }

  @Override
  public int hashCode() {
    return myChildRole.hashCode();
  }

  public static <E extends JpsElement> JpsElementCollectionRole<E> create(@NotNull JpsElementChildRole<E> role) {
    return new JpsElementCollectionRole<E>(role);
  }
}
