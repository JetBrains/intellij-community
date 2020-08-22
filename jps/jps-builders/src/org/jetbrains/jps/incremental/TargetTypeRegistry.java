// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.incremental;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.BuildTargetType;
import org.jetbrains.jps.service.JpsServiceManager;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public final class TargetTypeRegistry {
  private static final Logger LOG = Logger.getInstance(TargetTypeRegistry.class);
  private static final class Holder {
    static final TargetTypeRegistry ourInstance = new TargetTypeRegistry();
  }
  private final Map<String, BuildTargetType<?>> myTargetTypes = new LinkedHashMap<>();

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
