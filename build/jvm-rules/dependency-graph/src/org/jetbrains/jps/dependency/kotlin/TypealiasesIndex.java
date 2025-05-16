// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.kotlin;

import kotlin.metadata.KmDeclarationContainer;
import kotlin.metadata.KmTypeAlias;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.dependency.MapletFactory;
import org.jetbrains.jps.dependency.Node;
import org.jetbrains.jps.dependency.ReferenceID;
import org.jetbrains.jps.dependency.impl.BackDependencyIndexImpl;
import org.jetbrains.jps.dependency.java.JvmClass;
import org.jetbrains.jps.dependency.java.JvmNodeReferenceID;
import org.jetbrains.jps.util.Iterators;

import java.util.Collections;

public final class TypealiasesIndex extends BackDependencyIndexImpl {
  public static final String NAME = "type-aliases";

  public TypealiasesIndex(@NotNull MapletFactory cFactory) {
    super(NAME, cFactory);
  }

  @Override
  public Iterable<ReferenceID> getIndexedDependencies(@NotNull Node<?, ?> node) {
    KmDeclarationContainer container = node instanceof JvmClass? KJvmUtils.getDeclarationContainer(node) : null;
    if (container == null) {
      return Collections.emptyList();
    }

    String pkgName = ((JvmClass)node).getPackageName();
    Iterators.Function<KmTypeAlias, ReferenceID> mapper =
            pkgName.isBlank()?
            alias -> new JvmNodeReferenceID(alias.getName()) :
            alias -> new JvmNodeReferenceID(pkgName + "." + alias.getName());

    return Iterators.map(container.getTypeAliases(), mapper);
  }
}
