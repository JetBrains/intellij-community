/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.jps.model.serialization.jarRepository;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.serialization.JpsModelSerializerExtension;
import org.jetbrains.jps.model.serialization.JpsProjectExtensionSerializer;

import java.util.Collections;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 */
public class JpsRemoteRepositoriesModelSerializerExtension extends JpsModelSerializerExtension{
  private static final JpsRemoteRepositoriesConfigurationSerializer SERIALIZER_IMPL = new JpsRemoteRepositoriesConfigurationSerializer();

  @NotNull
  @Override
  public List<? extends JpsProjectExtensionSerializer> getProjectExtensionSerializers() {
    return Collections.singletonList(SERIALIZER_IMPL);
  }
}
