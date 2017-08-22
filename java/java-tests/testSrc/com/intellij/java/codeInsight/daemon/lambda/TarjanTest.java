/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.codeInsight.daemon.lambda;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.impl.source.resolve.graphInference.InferenceVariablesOrder;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Testdata from https://github.com/bwesterb/py-tarjan
 *
 * @author anna
 * @since 4.07.2013
 */
public class TarjanTest {
  @Test
  public void testResultGraph() {
    List<List<InferenceVariablesOrder.InferenceGraphNode<Integer>>> tarjan = InferenceVariablesOrder.tarjan(getNodes());
    String messages = StringUtil.join(tarjan, nodes -> StringUtil.join(nodes, node -> String.valueOf(node.getValue()), ", "), "\n");
    assertEquals("[9]\n" +
                 "[8], [7], [6]\n" +
                 "[5]\n" +
                 "[2], [1]\n" +
                 "[4], [3]", messages);
  }

  @Test
  public void testAcyclic() {
    List<InferenceVariablesOrder.InferenceGraphNode<Integer>> acyclicNodes = InferenceVariablesOrder.initNodes(getNodes());
    String messages = StringUtil.join(acyclicNodes, node -> String.valueOf(node.getValue()), "\n");
    assertEquals("[9]\n" +
                 "[8, 7, 6]\n" +
                 "[5]\n" +
                 "[2, 1]\n" +
                 "[4, 3]", messages);
  }

  //{1:[2],2:[1,5],3:[4],4:[3,5],5:[6],6:[7],7:[8],8:[6,9],9:[]}
  private static List<InferenceVariablesOrder.InferenceGraphNode<Integer>> getNodes() {
    List<InferenceVariablesOrder.InferenceGraphNode<Integer>> nodes = new ArrayList<>(9);

    for (int i = 0; i < 9; i++) {
      nodes.add(new InferenceVariablesOrder.InferenceGraphNode<>(i + 1));
    }

    nodes.get(0).getDependencies().add(nodes.get(1));

    nodes.get(1).getDependencies().add(nodes.get(0));
    nodes.get(1).getDependencies().add(nodes.get(4));

    nodes.get(2).getDependencies().add(nodes.get(3));

    nodes.get(3).getDependencies().add(nodes.get(2));
    nodes.get(3).getDependencies().add(nodes.get(4));

    nodes.get(4).getDependencies().add(nodes.get(5));

    nodes.get(5).getDependencies().add(nodes.get(6));

    nodes.get(6).getDependencies().add(nodes.get(7));

    nodes.get(7).getDependencies().add(nodes.get(5));
    nodes.get(7).getDependencies().add(nodes.get(8));

    return nodes;
  }
}
