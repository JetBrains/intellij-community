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

import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.JpsElementChildRole;

/**
 * @author nik
 */
public class JpsElementChildRoleBase<E extends JpsElement> extends JpsElementChildRole<E> {
  private final String myDebugName;

  protected JpsElementChildRoleBase(String debugName) {
    myDebugName = debugName;
  }

  @Override
  public String toString() {
    return myDebugName;
  }

  public static <E extends JpsElement> JpsElementChildRoleBase<E> create(String debugName) {
    return new JpsElementChildRoleBase<>(debugName);
  }
}
