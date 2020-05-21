// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.model.module;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.ex.JpsElementTypeBase;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class UnknownSourceRootType extends JpsElementTypeBase<UnknownSourceRootTypeProperties<?>> implements JpsModuleSourceRootType<UnknownSourceRootTypeProperties<?>> {
  private static final Map<String, UnknownSourceRootType> ourTypeNameToInstanceMap = new ConcurrentHashMap<>();
  private final String myUnknownTypeId;
  private final boolean myForTests;

  private UnknownSourceRootType(String unknownTypeId, boolean forTests) {
    myUnknownTypeId = unknownTypeId;
    myForTests = forTests;
  }

  public String getUnknownTypeId() {
    return myUnknownTypeId;
  }

  @Override
  public boolean isForTests() {
    return myForTests;
  }

  @NotNull
  @Override
  public UnknownSourceRootTypeProperties<?> createDefaultProperties() {
    return new UnknownSourceRootTypeProperties<>(null);
  }

  public static UnknownSourceRootType getInstance(String typeId, boolean forTests) {
    return ourTypeNameToInstanceMap.computeIfAbsent((forTests? "tst:" : "src:") + typeId, id -> new UnknownSourceRootType(typeId, forTests));
  }
}
