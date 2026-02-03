// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.kotlin;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.dependency.MapletFactory;
import org.jetbrains.jps.dependency.Node;
import org.jetbrains.jps.dependency.ReferenceID;
import org.jetbrains.jps.dependency.impl.BackDependencyIndexImpl;
import org.jetbrains.jps.dependency.java.JvmNodeReferenceID;
import org.jetbrains.jps.dependency.java.LookupNameUsage;
import org.jetbrains.jps.util.Iterators;


public final class LookupsIndex extends BackDependencyIndexImpl {
  public static final String NAME = "lookups";

  public LookupsIndex(@NotNull MapletFactory cFactory) {
    super(NAME, cFactory);
  }

  @Override
  public Iterable<ReferenceID> getIndexedDependencies(@NotNull Node<?, ?> node) {
    return Iterators.map(
      Iterators.filter(node.getUsages(), usage -> usage instanceof LookupNameUsage),
      usage -> {
        LookupNameUsage lookupNameUsage = (LookupNameUsage)usage;
        String ownerName = lookupNameUsage.getElementOwner().getNodeName();
        String name = lookupNameUsage.getName();

        return ownerName == null || ownerName.isEmpty()
               ? new JvmNodeReferenceID(name)
               : new JvmNodeReferenceID(ownerName + "/" + name);
      });
  }
}