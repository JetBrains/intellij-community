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
package org.jetbrains.jps.incremental;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.BuildTargetType;
import org.jetbrains.jps.service.JpsServiceManager;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public class TargetTypeRegistry {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.incremental.TargetTypeRegistry");
  private static class Holder {
    static final TargetTypeRegistry ourInstance = new TargetTypeRegistry();
  }
  private final Map<String, BuildTargetType<?>> myTargetTypes = new LinkedHashMap<String, BuildTargetType<?>>();

  public static TargetTypeRegistry getInstance() {
    return Holder.ourInstance;
  }

  private TargetTypeRegistry() {
    for (BuilderService service : JpsServiceManager.getInstance().getExtensions(BuilderService.class)) {
      for (BuildTargetType<?> type : service.getTargetTypes()) {
        String id = type.getTypeId();
        BuildTargetType<?> old = myTargetTypes.put(id, type);
        if (old != null) {
          LOG.error("Two build target types (" + type + ", " + old + ") use same id (" + id + ")");
        }
      }
    }
  }

  @Nullable
  public BuildTargetType<?> getTargetType(String typeId) {
    return myTargetTypes.get(typeId);
  }


  public Collection<BuildTargetType<?>> getTargetTypes() {
    return myTargetTypes.values();
  }

}
