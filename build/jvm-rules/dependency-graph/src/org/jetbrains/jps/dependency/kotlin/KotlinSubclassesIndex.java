// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.kotlin;

import kotlin.metadata.KmClassifier;
import kotlin.metadata.KmType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.dependency.MapletFactory;
import org.jetbrains.jps.dependency.Node;
import org.jetbrains.jps.dependency.ReferenceID;
import org.jetbrains.jps.dependency.impl.BackDependencyIndexImpl;
import org.jetbrains.jps.dependency.java.JvmClass;
import org.jetbrains.jps.dependency.java.JvmNodeReferenceID;
import org.jetbrains.jps.dependency.java.KotlinMeta;
import org.jetbrains.jps.util.Iterators;

import java.util.Collections;
import java.util.Objects;

/**
 * <code>KotlinSubclassesIndex</code> back-dependency index for Kotlin classes that tracks direct subclass relationships.
 * This index is used by the <code>KotlinCompilerReferenceIndex</code>.
 *
 * <p>The index is built based on <b>Kotlin metadata</b> and applies several filters:
 * <ul>
 *   <li>Java classes are excluded from values, so only JvmClass nodes with Kotlin metadata are accepted.
 *   In fact, this means that this index stores relations like: [Kotlin/Java]Nodes to [Kotlin]Nodes</li>
 *   <li>Only Kotlin classes with classKind = CLASS are indexed (filters out annotations, Kotlin file facades, etc.)</li>
 *   <li>Local classes are excluded from indexing</li>
 *   <li>The kotlin.Any supertype is filtered out as it's implicit for all Kotlin classes</li>
 * </ul>
 */
public class KotlinSubclassesIndex extends BackDependencyIndexImpl {
  public static final String NAME = "kotlin-direct-subclasses";

  public KotlinSubclassesIndex(@NotNull MapletFactory cFactory) {
    super(NAME, cFactory);
  }

  @Override
  public Iterable<ReferenceID> getIndexedDependencies(@NotNull Node<?, ?> node) {
    if (!(node instanceof JvmClass)) {
      return Collections.emptyList();
    }
    JvmClass classNode = (JvmClass)node;

    KotlinMeta kotlinMeta = KJvmUtils.getKotlinMeta(classNode);
    if (kotlinMeta == null || kotlinMeta.getKind() != 1 // CLASS
        || classNode.isLocal()) {
      return Collections.emptyList();
    }

    Iterable<String> fqNames =
      Iterators.filter(
        Iterators.map(
          Iterators.filter(
            Iterators.map(kotlinMeta.getSupertypes(), KmType::getClassifier),
            kmClassifier -> kmClassifier instanceof KmClassifier.Class
          ),
          kmClassifier -> ((KmClassifier.Class)kmClassifier).getName()
        ), fqName -> !Objects.equals(fqName, "kotlin/Any")
      );

    return Iterators.map(fqNames, name -> new JvmNodeReferenceID(name));
  }
}
