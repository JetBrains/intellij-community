/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInspection.ui;

import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ui.tree.TreeUtil;
import gnu.trove.THashMap;

import java.util.Map;
import java.util.function.Supplier;

/**
 * @author dima
 */
//guarded by single tree rebuild thread
public class InspectionTreeNodeReuser {
  private final static Logger LOG = Logger.getInstance(InspectionTreeNodeReuser.class);
  private final Map<CommonProblemDescriptor, ProblemDescriptionNode> myReusedNodes = new THashMap<>();

  public InspectionTreeNodeReuser(InspectionTree tree) {
    TreeUtil.traverse(tree.getRoot(), n -> {
      if (n instanceof ProblemDescriptionNode) {
        CommonProblemDescriptor descriptor = ((ProblemDescriptionNode)n).getDescriptor();
        if (descriptor != null) {
          myReusedNodes.put(descriptor, (ProblemDescriptionNode)n);
        }
      }
      return true;
    });
  }

  public ProblemDescriptionNode getOrCreate(CommonProblemDescriptor descriptor, Supplier<ProblemDescriptionNode> produces) {
    ProblemDescriptionNode node = myReusedNodes.get(descriptor);
    if (node != null) {
      return node;
    }
    return produces.get();
  }
}
