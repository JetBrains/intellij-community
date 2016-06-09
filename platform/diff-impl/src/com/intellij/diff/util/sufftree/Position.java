package com.intellij.diff.util.sufftree;

public class Position {
  public Vertex vertex;
  public int symbolsDown; // order of the symbol in the edge (from 1 to vertex.length)

  public Position(Vertex vertex) {
    this.vertex = vertex;
    symbolsDown = vertex.length;
  }

  public Position(Vertex vertex, int symbolsDown) {
    this.vertex = vertex;
    this.symbolsDown = symbolsDown;
  }

  public boolean isInVertex() {
    return symbolsDown == vertex.length;
  }
}
