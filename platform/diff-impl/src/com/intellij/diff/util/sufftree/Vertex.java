package com.intellij.diff.util.sufftree;

public class Vertex<T> extends Vertices.Entry {
  // In the leaf - link1 and link2 are prev and next leaves
  // Else - link1 and link2 are first and last ancestor leaves
  public Vertex parentLink, suffixLink, traverseLink1, traverseLink2;
  public int symbolIndex, length;
  public int firstSymbol;
  public int oneOfSuffixes; // start index of the suffix
  public final static int leafMarker = Integer.MAX_VALUE;

  // Creating root
  public Vertex(int oneOfSuffixes) {
    this.oneOfSuffixes = oneOfSuffixes;
    length = 0;
  }

  // For queries
  public Vertex(Vertex parentLink, int firstSymbol) {
    this.parentLink = parentLink;
    this.firstSymbol = firstSymbol;
  }

  // Creating leaf
  public Vertex(Vertex parentLink, int symbolIndex, int firstSymbol, int oneOfSuffixes) {
    this.parentLink = parentLink;
    this.symbolIndex = symbolIndex;
    this.firstSymbol = firstSymbol;
    this.oneOfSuffixes = oneOfSuffixes;
    length = leafMarker;
  }

  // Creating inner vertex
  public Vertex(Vertex parentLink, int symbolIndex, int length, int firstSymbol, int oneOfSuffixes) {
    this.parentLink = parentLink;
    this.symbolIndex = symbolIndex;
    this.length = length;
    this.firstSymbol = firstSymbol;
    this.oneOfSuffixes = oneOfSuffixes;
  }

  public boolean isLeaf() {
    return length == leafMarker;
  }

  public boolean equals(Object o) {
    if (!(o instanceof Vertex)) {
      return false;
    }

    Vertex edge = (Vertex)o;
    return (firstSymbol == edge.firstSymbol &&
            parentLink == edge.parentLink);
  }

  public int hashCode2() {
    return 29 * parentLink.hashCode() + (int)firstSymbol;
  }
}
