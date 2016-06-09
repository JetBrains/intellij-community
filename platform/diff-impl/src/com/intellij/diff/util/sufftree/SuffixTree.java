package com.intellij.diff.util.sufftree;

/* Implementation of Suffix Trees
   Based on Ukkonen's O(n) algorithm
   Described in "Algorithms on Strings, Trees ans Sequences" Dan Gusfield, 1997
*/

import gnu.trove.TIntArrayList;

public class SuffixTree {
  private TIntArrayList provider;
  private Vertices edges;
  private int length;
  private Vertex root;

  public void build(TIntArrayList provider) {
    this.provider = provider;
    length = provider.size();
    root = new Vertex(0);

    if (length == 0) {
      edges = new Vertices(1);
      return;
    }

    //        edges = new Vertices(( int ) (( length - 1 ) * 2 / 0.75 ));
    edges = new Vertices(length * 2);
    int curInd = 1; // curInd - start index of current suffix

    // Building tree T1
    Vertex cur, leaf = new Vertex(root, 0, provider.get(0), 0);
    edges.put(leaf);
    root.traverseLink1 = root.traverseLink2 = leaf;
    cur = leaf;

    // Building trees from T2 to Tn
    for (int i = 1; i < length; i++) // i - length of already built prefix
    {
      for (; curInd <= i; curInd++) {
        Vertex prev = cur;
        Position pos = findNextSuffix(cur, i);

        if (descend(pos, provider.get(i)) != null) {
          if (!prev.isLeaf() && prev.suffixLink == null) {
            if (!pos.isInVertex()) {
              throw new RuntimeException("Must be in vertex");
            }

            prev.suffixLink = pos.vertex;
          }

          break;
        }

        leaf = splitAndAdd(pos, i, curInd);
        cur = leaf.parentLink;

        if (!prev.isLeaf() && prev.suffixLink == null) {
          prev.suffixLink = cur;
        }
      }

      cur = leaf;
    }
  }

  private Position findNextSuffix(Vertex v, int curLength) {
    int length = (v.length == Vertex.leafMarker) ? curLength - v.symbolIndex : v.length;

    if (v.suffixLink != null) {
      return new Position(v.suffixLink);
    }
    else {
      if (v.parentLink.suffixLink != null) {
        return descend(v.parentLink.suffixLink, v.symbolIndex, length);
      }
      else if (v.parentLink == root) {
        return descend(root, v.symbolIndex + 1, length - 1);
      }
      else {
        throw new RuntimeException("Cannot find the suffix link");
      }
    }
  }

  private Vertex splitAndAdd(Position position, int symbolIndex, int suffixStart) {
    Vertex vertex = position.vertex;

    if (!position.isInVertex()) {
      edges.delete(vertex);

      Vertex v = new Vertex(vertex.parentLink, vertex.symbolIndex,
                            position.symbolsDown, vertex.firstSymbol, vertex.oneOfSuffixes);

      if (vertex.isLeaf()) {
        v.traverseLink1 = v.traverseLink2 = vertex;
      }
      else {
        v.traverseLink1 = vertex.traverseLink1;
        v.traverseLink2 = vertex.traverseLink2;
      }

      vertex.parentLink = v;
      vertex.symbolIndex += position.symbolsDown;
      vertex.firstSymbol = provider.get(vertex.symbolIndex);

      if (vertex.length != Vertex.leafMarker) {
        vertex.length -= position.symbolsDown;
      }

      edges.put(vertex);
      edges.put(v);
      vertex = v;
    }

    return addLeafEdge(vertex, symbolIndex, suffixStart);
  }

  private Vertex addLeafEdge(Vertex vertex, int symbolIndex, int suffixStart) {
    Vertex v = new Vertex(vertex, symbolIndex, provider.get(symbolIndex), suffixStart);

    //Insert new kid at the end of the sublist
    Vertex lastKid = v.parentLink.traverseLink2;
    v.traverseLink1 = lastKid.traverseLink1;
    v.traverseLink2 = lastKid;
    lastKid.traverseLink1 = v;

    if (v.traverseLink1 != null) {
      v.traverseLink1.traverseLink2 = v;
    }

    //Patch the last kids of all the path to this kid
    Vertex cur = v.parentLink;
    while (cur != null) {
      cur.traverseLink2 = v;
      cur = cur.parentLink;
    }

    edges.put(v);
    return v;
  }

  public Position getRootPosition() {
    if (root == null) {
      throw new RuntimeException("Tree was not built yet");
    }

    return new Position(root);
  }

  public Position descend(Position position, int symbol) {
    Vertex vertex = position.vertex;

    if (!position.isInVertex()) // Still on the edge
    {
      if (vertex.symbolIndex + position.symbolsDown < length &&
          provider.get(vertex.symbolIndex + position.symbolsDown) == symbol) {
        return new Position(vertex, position.symbolsDown + 1);
      }
      else {
        return null;
      }
    }
    else // In the vertex
    {
      Vertex v = (Vertex)edges.search(new Vertex(vertex, symbol));

      if (v != null) {
        return new Position(v, 1);
      }
      else {
        return null;
      }
    }
  }

  private Position descend(Vertex vertex, int symbolIndex, int length) {
    if (length == 0) {
      return new Position(vertex);
    }

    while (true) {
      Vertex v = (Vertex)edges.search(new Vertex(vertex, provider.get(symbolIndex)));

      if (v == null) {
        throw new RuntimeException("The tree doesn't contain this suffix");
      }

      if (length <= v.length) {
        return new Position(v, length);
      }

      length -= v.length;
      symbolIndex += v.length;
      vertex = v;
    }
  }

  public void matchString(TIntArrayList string, StringMatchingFunction function) {
    Position prevPos = null, pos = getRootPosition();
    int prevIndex = 0, stringLength = string.size();

    for (int i = 0; i < stringLength; i++) {
      prevPos = pos;
      pos = descend(pos, string.get(i));

      if (pos == null) {
        if (i != prevIndex) {
          function.match(prevIndex, prevPos, i - prevIndex);
          i--;
        }

        prevIndex = i + 1;
        pos = getRootPosition();
      }
    }

    if (prevIndex < stringLength) {
      function.match(prevIndex, pos, stringLength - prevIndex);
    }
  }

  public void iterateSuffixes(Vertex vertex, SuffixesIteratingFunction function) {
    if (vertex.isLeaf()) {
      function.iterate(vertex.oneOfSuffixes);
    }
    else {
      for (Vertex cur = vertex.traverseLink1; true; cur = cur.traverseLink1) {
        function.iterate(cur.oneOfSuffixes);
        if (cur == vertex.traverseLink2) {
          break;
        }
      }
    }
  }

  public interface StringMatchingFunction {
    void match(int posInString, Position posInTree, int length);
  }

  public interface SuffixesIteratingFunction {
    void iterate(int suffixPos);
  }
}
