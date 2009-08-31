package com.intellij.internal.diGraph.impl;

import com.intellij.internal.diGraph.analyzer.Mark;
import com.intellij.internal.diGraph.analyzer.MarkedNode;

import java.util.Iterator;
import java.util.LinkedList;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: 21.06.2003
 * Time: 23:35:24
 * To change this template use Options | File Templates.
 */
public class NodeImpl implements MarkedNode {
  LinkedList<EdgeImpl> myIn;
  LinkedList<EdgeImpl> myOut;

  public NodeImpl() {
    myIn = new LinkedList<EdgeImpl>();
    myOut = new LinkedList<EdgeImpl>();
  }

  public NodeImpl(EdgeImpl[] in, EdgeImpl[] out) {
    myIn = new LinkedList<EdgeImpl>();
    myOut = new LinkedList<EdgeImpl>();

    for (int i = 0; i < (in == null ? 0 : in.length); i++) {
      myIn.add(in[i]);
      in[i].myEnd = this;
    }

    for (int i = 0; i < (out == null ? 0 : out.length); i++) {
      myOut.add(out[i]);
      out[i].myBeg = this;
    }
  }

  public NodeImpl(LinkedList<EdgeImpl> in, LinkedList<EdgeImpl> out) {
    myIn = in == null ? new LinkedList<EdgeImpl>() : in;
    myOut = out == null ? new LinkedList<EdgeImpl>() : out;

    for (EdgeImpl aMyIn : myIn) aMyIn.myEnd = this;
    for (EdgeImpl aMyOut : myOut) aMyOut.myBeg = this;
  }

  public Iterator<EdgeImpl> inIterator() {
    return myIn.iterator();
  }

  public Iterator<EdgeImpl> outIterator() {
    return myOut.iterator();
  }

  public int inDeg() {
    return myIn.size();
  }

  public int outDeg() {
    return myOut.size();
  }

  public Mark getMark() {
    return null;
  }

  public void setMark(Mark x) {

  }
}
