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
package org.jetbrains.jps.model.serialization.library;

import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.library.sdk.JpsSdkType;
import org.jetbrains.jps.model.serialization.JpsElementPropertiesSerializer;

public abstract class JpsSdkPropertiesSerializer<P extends JpsElement> extends JpsElementPropertiesSerializer<P, JpsSdkType<P>> {

  protected JpsSdkPropertiesSerializer(String typeId, JpsSdkType<P> type) {
    super(type, typeId);
  }

  @NotNull
  public abstract P loadProperties(@Nullable Element propertiesElement);

  /**
   * @deprecated the build process doesn't save project configuration so there is no need to implement this method, it isn't called by the platform
   */
  @Deprecated
  public void saveProperties(@NotNull P properties, @NotNull Element element) {
  }
}
