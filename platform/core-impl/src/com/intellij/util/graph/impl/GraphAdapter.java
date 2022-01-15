// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.graph.impl;

import com.intellij.util.graph.ElementOrder;
import com.intellij.util.graph.EndpointPair;
import com.intellij.util.graph.Graph;
import com.intellij.util.graph.MutableNetwork;
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
    return new MutableNetwork<N, E>() {
      @Override
      public boolean addNode(N n) {
        return delegate.addNode(n);
      }

      @Override
      public boolean addEdge(N n, N n1, E e) {
        return delegate.addEdge(n, n1, e);
      }

      @Override
      public boolean addEdge(EndpointPair<N> endpoints, E edge) {
        return delegate.addEdge(unwrapEndpointsPair(endpoints), edge);
      }

      @Override
      public boolean removeNode(N n) {
        return delegate.removeNode(n);
      }

      @Override
      public boolean removeEdge(E e) {
        return delegate.removeEdge(e);
      }

      @Override
      public Set<N> nodes() {
        return delegate.nodes();
      }

      @Override
      public Set<E> edges() {
        return delegate.edges();
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
        return delegate.isDirected();
      }

      @Override
      public boolean allowsParallelEdges() {
        return delegate.allowsParallelEdges();
      }

      @Override
      public boolean allowsSelfLoops() {
        return delegate.allowsSelfLoops();
      }

      @Override
      public ElementOrder<N> nodeOrder() {
        return wrapOrder(delegate.nodeOrder());
      }

      @Override
      public ElementOrder<E> edgeOrder() {
        return wrapOrder(delegate.edgeOrder());
      }

      @Override
      public Set<N> adjacentNodes(N n) {
        return delegate.adjacentNodes(n);
      }

      @Override
      public Set<N> predecessors(N n) {
        return delegate.predecessors(n);
      }

      @Override
      public Set<N> successors(N n) {
        return delegate.successors(n);
      }

      @Override
      public Set<E> incidentEdges(N n) {
        return delegate.incidentEdges(n);
      }

      @Override
      public Set<E> inEdges(N n) {
        return delegate.inEdges(n);
      }

      @Override
      public Set<E> outEdges(N n) {
        return delegate.outEdges(n);
      }

      @Override
      public int degree(N n) {
        return delegate.degree(n);
      }

      @Override
      public int inDegree(N n) {
        return delegate.inDegree(n);
      }

      @Override
      public int outDegree(N n) {
        return delegate.outDegree(n);
      }

      @Override
      public EndpointPair<N> incidentNodes(E e) {
        return wrapEndpointsPair(delegate.incidentNodes(e));
      }

      @Override
      public Set<E> adjacentEdges(E e) {
        return delegate.adjacentEdges(e);
      }

      @Override
      public Set<E> edgesConnecting(N n, N n1) {
        return delegate.edgesConnecting(n, n1);
      }

      @Override
      public Optional<E> edgeConnecting(N n, N n1) {
        return delegate.edgeConnecting(n, n1);
      }

      @Override
      public E edgeConnectingOrNull(N n, N n1) {
        return delegate.edgeConnectingOrNull(n, n1);
      }

      @Override
      public Set<E> edgesConnecting(EndpointPair<N> endpoints) {
        return delegate.edgesConnecting(unwrapEndpointsPair(endpoints));
      }

      @Override
      public Optional<E> edgeConnecting(EndpointPair<N> endpoints) {
        return delegate.edgeConnecting(unwrapEndpointsPair(endpoints));
      }

      @Override
      public @Nullable E edgeConnectingOrNull(EndpointPair<N> endpoints) {
        return delegate.edgeConnectingOrNull(unwrapEndpointsPair(endpoints));
      }

      @Override
      public boolean hasEdgeConnecting(N n, N n1) {
        return delegate.hasEdgeConnecting(n, n1);
      }

      @Override
      public boolean hasEdgeConnecting(EndpointPair<N> endpoints) {
        return delegate.hasEdgeConnecting(unwrapEndpointsPair(endpoints));
      }

      @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
      @Override
      public boolean equals(@Nullable Object o) {
        return delegate.equals(o);
      }

      @Override
      public int hashCode() {
        return delegate.hashCode();
      }
    };
  }
}
