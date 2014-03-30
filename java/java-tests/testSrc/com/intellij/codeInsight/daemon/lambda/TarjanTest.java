/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.lambda;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.impl.source.resolve.graphInference.InferenceVariablesOrder;
import com.intellij.util.Function;
import junit.framework.Assert;
import junit.framework.TestCase;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * User: anna
 * Date: 7/4/13
 */
public class TarjanTest extends TestCase {

  //testdata from https://github.com/bwesterb/py-tarjan/
  public void testResultGraph() throws Exception {
    

    final List<List<InferenceVariablesOrder.InferenceGraphNode<Integer>>> tarjan = InferenceVariablesOrder.tarjan(Arrays.asList(initNodes()));
    final String messages = StringUtil.join(tarjan, new Function<List<InferenceVariablesOrder.InferenceGraphNode<Integer>>, String>() {
      @Override
      public String fun(List<InferenceVariablesOrder.InferenceGraphNode<Integer>> nodes) {
        return StringUtil.join(nodes, new Function<InferenceVariablesOrder.InferenceGraphNode<Integer>, String>() {
          @Override
          public String fun(InferenceVariablesOrder.InferenceGraphNode<Integer> node) {
            return String.valueOf(node.getValue());
          }
        }, ",");
      }
    }, "\n");

    Assert.assertEquals("[9]\n" +
                        "[8],[7],[6]\n" +
                        "[5]\n" +
                        "[2],[1]\n" +
                        "[4],[3]", messages);

    final ArrayList<InferenceVariablesOrder.InferenceGraphNode<Integer>> acyclicNodes = InferenceVariablesOrder.initNodes(Arrays.asList(initNodes()));
    final String aMessages = StringUtil.join(acyclicNodes, new Function<InferenceVariablesOrder.InferenceGraphNode<Integer>, String>() {
      @Override
      public String fun(InferenceVariablesOrder.InferenceGraphNode<Integer> node) {
        return String.valueOf(node.getValue());
      }
    }, ",");


    Assert.assertEquals("[9],[8, 7, 6],[5],[2, 1],[4, 3]", aMessages);
  }

  //{1:[2],2:[1,5],3:[4],4:[3,5],5:[6],6:[7],7:[8],8:[6,9],9:[]}
  private static InferenceVariablesOrder.InferenceGraphNode<Integer>[] initNodes() {
    InferenceVariablesOrder.InferenceGraphNode<Integer>[] nodes = new InferenceVariablesOrder.InferenceGraphNode[9];
    for (int i = 0; i < nodes.length; i++) {
      nodes[i] = new InferenceVariablesOrder.InferenceGraphNode<Integer>(i + 1);
    }

    nodes[0].getDependencies().add(nodes[1]);

    nodes[1].getDependencies().add(nodes[0]);
    nodes[1].getDependencies().add(nodes[4]);

    nodes[2].getDependencies().add(nodes[3]);

    nodes[3].getDependencies().add(nodes[2]);
    nodes[3].getDependencies().add(nodes[4]);

    nodes[4].getDependencies().add(nodes[5]);

    nodes[5].getDependencies().add(nodes[6]);

    nodes[6].getDependencies().add(nodes[7]);

    nodes[7].getDependencies().add(nodes[5]);
    nodes[7].getDependencies().add(nodes[8]);
    return nodes;
  }
}
