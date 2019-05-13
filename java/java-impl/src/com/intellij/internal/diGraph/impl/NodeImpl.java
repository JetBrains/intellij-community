/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.internal.diGraph.impl;

import com.intellij.internal.diGraph.analyzer.Mark;
import com.intellij.internal.diGraph.analyzer.MarkedNode;

import java.util.Iterator;
import java.util.LinkedList;

public class NodeImpl implements MarkedNode {
  LinkedList<EdgeImpl> myIn;
  LinkedList<EdgeImpl> myOut;

  public NodeImpl() {
    myIn = new LinkedList<>();
    myOut = new LinkedList<>();
  }

  public NodeImpl(EdgeImpl[] in, EdgeImpl[] out) {
    myIn = new LinkedList<>();
    myOut = new LinkedList<>();

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
    myIn = in == null ? new LinkedList<>() : in;
    myOut = out == null ? new LinkedList<>() : out;

    for (EdgeImpl aMyIn : myIn) aMyIn.myEnd = this;
    for (EdgeImpl aMyOut : myOut) aMyOut.myBeg = this;
  }

  @Override
  public Iterator<EdgeImpl> inIterator() {
    return myIn.iterator();
  }

  @Override
  public Iterator<EdgeImpl> outIterator() {
    return myOut.iterator();
  }

  @Override
  public int inDeg() {
    return myIn.size();
  }

  @Override
  public int outDeg() {
    return myOut.size();
  }

  @Override
  public Mark getMark() {
    return null;
  }

  @Override
  public void setMark(Mark x) {

  }
}
