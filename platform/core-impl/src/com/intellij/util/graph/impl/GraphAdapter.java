// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.graph.impl;

import com.intellij.util.graph.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;


public final class GraphAdapter {

  private GraphAdapter() { }

  public static <N> EndpointPair<N> wrapEndpointsPair(com.google.common.graph.EndpointPair<N> endpoints) {
    if (endpoints.isOrdered()) {
      return EndpointPair.ordered(endpoints.source(), endpoints.target());
    }
    else {
      return EndpointPair.unordered(endpoints.nodeU(), endpoints.nodeV());
    }
  }

  public static <N> com.google.common.graph.EndpointPair<N> unwrapEndpointsPair(EndpointPair<N> endpoints) {
    if (endpoints.isOrdered()) {
      return com.google.common.graph.EndpointPair.ordered(endpoints.source(), endpoints.target());
    }
    else {
      return com.google.common.graph.EndpointPair.unordered(endpoints.nodeU(), endpoints.nodeV());
    }
  }

  public static <T> ElementOrder<T> wrapOrder(com.google.common.graph.ElementOrder<T> order) {
    switch (order.type()) {
      case STABLE:
        return ElementOrder.stable();
      case INSERTION:
        return ElementOrder.insertion();
      case SORTED:
        return ElementOrder.sorted(order.comparator());
      case UNORDERED:
      default:
        return ElementOrder.unordered();
    }
  }

  public static <T> com.google.common.graph.ElementOrder<T> unwrapOrder(ElementOrder<T> order) {
    switch (order.type()) {
      case STABLE:
        return com.google.common.graph.ElementOrder.stable();
      case INSERTION:
        return com.google.common.graph.ElementOrder.insertion();
      case SORTED:
        return com.google.common.graph.ElementOrder.sorted(order.comparator());
      case UNORDERED:
      default:
        return com.google.common.graph.ElementOrder.unordered();
    }
  }

  public static <N, E> MutableNetwork<N, E> wrapNetwork(com.google.common.graph.MutableNetwork<N, E> delegate) {
    return new MyMutableNetworkWrapper<>(delegate);
  }

  public static <N, E> com.google.common.graph.@NotNull MutableNetwork<N, E> unwrapNetwork(@NotNull Network<N, E> network) {
    return ((MyMutableNetworkWrapper<N, E>)network).getDelegate();
  }


  private static final class MyMutableNetworkWrapper<N, E> implements MutableNetwork<N, E> {

    private final @NotNull com.google.common.graph.MutableNetwork<N, E> myDelegate;

    private MyMutableNetworkWrapper(@NotNull com.google.common.graph.MutableNetwork<N, E> delegate) {
      myDelegate = delegate;
    }

    @NotNull com.google.common.graph.MutableNetwork<N, E> getDelegate() {
      return myDelegate;
    }

    @Override
    public boolean addNode(N n) {
      return myDelegate.addNode(n);
    }

    @Override
    public boolean addEdge(N n, N n1, E e) {
      return myDelegate.addEdge(n, n1, e);
    }

    @Override
    public boolean addEdge(EndpointPair<N> endpoints, E edge) {
      return myDelegate.addEdge(unwrapEndpointsPair(endpoints), edge);
    }

    @Override
    public boolean removeNode(N n) {
      return myDelegate.removeNode(n);
    }

    @Override
    public boolean removeEdge(E e) {
      return myDelegate.removeEdge(e);
    }

    @Override
    public Set<N> nodes() {
      return myDelegate.nodes();
    }

    @Override
    public Set<E> edges() {
      return myDelegate.edges();
    }

    @Override
    public Graph<N> asGraph() {
      return new Graph<N>() {
        @Override
        public @NotNull Collection<N> getNodes() {
          return nodes();
        }

        @Override
        public @NotNull Iterator<N> getIn(N n) {
          return predecessors(n).iterator();
        }

        @Override
        public @NotNull Iterator<N> getOut(N n) {
          return successors(n).iterator();
        }
      };
    }

    @Override
    public boolean isDirected() {
      return myDelegate.isDirected();
    }

    @Override
    public boolean allowsParallelEdges() {
      return myDelegate.allowsParallelEdges();
    }

    @Override
    public boolean allowsSelfLoops() {
      return myDelegate.allowsSelfLoops();
    }

    @Override
    public ElementOrder<N> nodeOrder() {
      return wrapOrder(myDelegate.nodeOrder());
    }

    @Override
    public ElementOrder<E> edgeOrder() {
      return wrapOrder(myDelegate.edgeOrder());
    }

    @Override
    public Set<N> adjacentNodes(N n) {
      return myDelegate.adjacentNodes(n);
    }

    @Override
    public Set<N> predecessors(N n) {
      return myDelegate.predecessors(n);
    }

    @Override
    public Set<N> successors(N n) {
      return myDelegate.successors(n);
    }

    @Override
    public Set<E> incidentEdges(N n) {
      return myDelegate.incidentEdges(n);
    }

    @Override
    public Set<E> inEdges(N n) {
      return myDelegate.inEdges(n);
    }

    @Override
    public Set<E> outEdges(N n) {
      return myDelegate.outEdges(n);
    }

    @Override
    public int degree(N n) {
      return myDelegate.degree(n);
    }

    @Override
    public int inDegree(N n) {
      return myDelegate.inDegree(n);
    }

    @Override
    public int outDegree(N n) {
      return myDelegate.outDegree(n);
    }

    @Override
    public EndpointPair<N> incidentNodes(E e) {
      return wrapEndpointsPair(myDelegate.incidentNodes(e));
    }

    @Override
    public Set<E> adjacentEdges(E e) {
      return myDelegate.adjacentEdges(e);
    }

    @Override
    public Set<E> edgesConnecting(N n, N n1) {
      return myDelegate.edgesConnecting(n, n1);
    }

    @Override
    public Optional<E> edgeConnecting(N n, N n1) {
      return myDelegate.edgeConnecting(n, n1);
    }

    @Override
    public E edgeConnectingOrNull(N n, N n1) {
      return myDelegate.edgeConnectingOrNull(n, n1);
    }

    @Override
    public Set<E> edgesConnecting(EndpointPair<N> endpoints) {
      return myDelegate.edgesConnecting(unwrapEndpointsPair(endpoints));
    }

    @Override
    public Optional<E> edgeConnecting(EndpointPair<N> endpoints) {
      return myDelegate.edgeConnecting(unwrapEndpointsPair(endpoints));
    }

    @Override
    public @Nullable E edgeConnectingOrNull(EndpointPair<N> endpoints) {
      return myDelegate.edgeConnectingOrNull(unwrapEndpointsPair(endpoints));
    }

    @Override
    public boolean hasEdgeConnecting(N n, N n1) {
      return myDelegate.hasEdgeConnecting(n, n1);
    }

    @Override
    public boolean hasEdgeConnecting(EndpointPair<N> endpoints) {
      return myDelegate.hasEdgeConnecting(unwrapEndpointsPair(endpoints));
    }

    @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
    @Override
    public boolean equals(@Nullable Object o) {
      return myDelegate.equals(o);
    }

    @Override
    public int hashCode() {
      return myDelegate.hashCode();
    }
  }
}
