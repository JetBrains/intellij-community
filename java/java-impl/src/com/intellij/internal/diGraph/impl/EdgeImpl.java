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

import com.intellij.internal.diGraph.Edge;
import com.intellij.internal.diGraph.Node;
import com.intellij.internal.diGraph.analyzer.MarkedEdge;
import com.intellij.internal.diGraph.analyzer.Mark;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: 21.06.2003
 * Time: 23:38:12
 * To change this template use Options | File Templates.
 */
public class EdgeImpl implements MarkedEdge {
  NodeImpl myBeg;
  NodeImpl myEnd;

  public EdgeImpl() {
  }

  public EdgeImpl(NodeImpl from, NodeImpl to) {
    myBeg = from;
    myEnd = to;

    from.myOut.add(this);
    to.myIn.add(this);
  }

  public Node beg() {
    return myBeg;
  }

  public Node end() {
    return myEnd;
  }

  public Mark getMark(){
    return null;
  }

  public void setMark(Mark x){

  }
}
