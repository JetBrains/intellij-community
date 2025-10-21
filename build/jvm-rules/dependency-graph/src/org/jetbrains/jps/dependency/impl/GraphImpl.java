// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.impl;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.dependency.*;
import org.jetbrains.jps.dependency.java.JvmNodeReferenceID;
import org.jetbrains.jps.dependency.java.SubclassesIndex;
import org.jetbrains.jps.dependency.kotlin.TypealiasesIndex;
import org.jetbrains.jps.util.Iterators;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.util.*;
import java.util.function.Function;

import static org.jetbrains.jps.util.Iterators.*;

// this is a base implementation for shared functionality in both DependencyGraph and Delta
@ApiStatus.Internal
public abstract class GraphImpl implements Graph {
  private final BackDependencyIndex myDependencyIndex; // nodeId -> nodes, referencing the nodeId
  private final List<BackDependencyIndex> myIndices = new ArrayList<>();
  protected final MultiMaplet<ReferenceID, NodeSource> myNodeToSourcesMap;
  protected final MultiMaplet<NodeSource, Node<?, ?>> mySourceToNodesMap;
  private final MapletFactory myContainerFactory;
  private final IndexFactory myIndexFactory;
  private static final Set<String> ourMandatoryIndexNames = Set.of(
    NodeDependenciesIndex.NAME, SubclassesIndex.NAME, TypealiasesIndex.NAME
  );

  public interface IndexFactory {
    Iterable<BackDependencyIndex> createIndices(@NotNull MapletFactory cFactory);


    /**
     * An index factory creating set of mandatory indices that must be present in all graph instances
     */
    static IndexFactory mandatoryIndices() {
      return containerFactory -> List.of(
        new NodeDependenciesIndex(containerFactory),
        new SubclassesIndex(containerFactory),
        new TypealiasesIndex(containerFactory)
      );
    }

    /**
     *
     * @param extension indices
     * @return a combined index factory that creates all mandatory indices plus those specified  
     */
    static IndexFactory create(Function<MapletFactory, BackDependencyIndex>... extIndices) {
      IndexFactory mandatory = mandatoryIndices();
      return containerFactory -> collect(
        map(Arrays.asList(extIndices), extIndex -> extIndex.apply(containerFactory)),
        collect(mandatory.createIndices(containerFactory), new ArrayList<>())
      );
    }

  }

  protected GraphImpl(@NotNull MapletFactory cFactory, IndexFactory indexFactory) {
    myContainerFactory = cFactory;
    myIndexFactory = indexFactory;
    try {

      Set<String> registered = new HashSet<>();
      for (BackDependencyIndex index : indexFactory.createIndices(cFactory)) {
        myIndices.add(index);
        registered.add(index.getName());
      }

      if (!registered.containsAll(ourMandatoryIndexNames)) {
        throw new RuntimeException("Dependency Graph must contain following mandatory indices: " + ourMandatoryIndexNames + "\n\tCurrent registered indices: " + registered);
      }
      
      myDependencyIndex = find(myIndices, idx -> NodeDependenciesIndex.NAME.equals(idx.getName()));

      // important: if multiple implementations of NodeSource are available, change to generic graph element externalizer
      Externalizer<NodeSource> srcExternalizer = Externalizer.forGraphElement(PathSource::new, NodeSource[]::new);
      myNodeToSourcesMap = cFactory.createSetMultiMaplet("node-sources-map", Externalizer.forGraphElement(JvmNodeReferenceID::new, JvmNodeReferenceID[]::new), srcExternalizer);
      mySourceToNodesMap = cFactory.createSetMultiMaplet("source-nodes-map", srcExternalizer, Externalizer.forAnyGraphElement(Node<?, ?>[]::new));
    }
    catch (RuntimeException e) {
      closeIgnoreErrors();
      throw e;
    }
  }

  protected final IndexFactory getIndexFactory() {
    return myIndexFactory;
  }

  @Override
  public @NotNull Iterable<ReferenceID> getDependingNodes(@NotNull ReferenceID id) {
    return myDependencyIndex.getDependencies(id);
  }

  @Override
  public final Iterable<BackDependencyIndex> getIndices() {
    return myIndices;
  }

  @Override
  public final @Nullable BackDependencyIndex getIndex(String name) {
    for (BackDependencyIndex index : myIndices) {
      if (index.getName().equals(name)) {
        return index;
      }
    }
    return null;
  }

  @Override
  public Iterable<NodeSource> getSources(@NotNull ReferenceID id) {
    return myNodeToSourcesMap.get(id);
  }

  @Override
  public Iterable<ReferenceID> getRegisteredNodes() {
    return myNodeToSourcesMap.getKeys();
  }

  @Override
  public Iterable<NodeSource> getSources() {
    return mySourceToNodesMap.getKeys();
  }

  @Override
  public Iterable<Node<?, ?>> getNodes(@NotNull NodeSource source) {
    return mySourceToNodesMap.get(source);
  }

  protected final void closeIgnoreErrors() {
    try {
      close();
    }
    catch (Throwable ignored) {
    }
  }

  public void close() throws IOException {
    if (myContainerFactory instanceof Closeable)  {
      ((Closeable)myContainerFactory).close();
    }
  }
  
  public void flush() throws IOException {
    if (myContainerFactory instanceof Flushable)  {
      ((Flushable)myContainerFactory).flush();
    }
  }

}
