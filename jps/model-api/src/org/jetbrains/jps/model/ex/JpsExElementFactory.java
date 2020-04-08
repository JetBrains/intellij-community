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
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.JpsElementChildRole;
import org.jetbrains.jps.model.JpsElementCollection;
import org.jetbrains.jps.service.JpsServiceManager;

public abstract class JpsExElementFactory {
  public static JpsExElementFactory getInstance() {
    return InstanceHolder.INSTANCE;
  }

  public abstract <E extends JpsElement> JpsElementCollection<E> createCollection(JpsElementChildRole<E> role);

  public abstract JpsElementContainerEx createContainer(@NotNull JpsCompositeElementBase<?> parent);

  public abstract JpsElementContainerEx createContainerCopy(@NotNull JpsElementContainerEx original,
                                                            @NotNull JpsCompositeElementBase<?> parent);

  private static final class InstanceHolder {
    private static final JpsExElementFactory INSTANCE = JpsServiceManager.getInstance().getService(JpsExElementFactory.class);
  }
}
