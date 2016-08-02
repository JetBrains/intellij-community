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
package com.intellij.psi.impl.java.stubs;

import com.intellij.lang.LighterAST;
import com.intellij.lang.LighterASTNode;
import com.intellij.psi.impl.source.JavaLightTreeUtil;
import com.intellij.psi.impl.source.tree.RecursiveLighterASTNodeWalkingVisitor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.intellij.psi.impl.source.tree.JavaElementType.*;

/**
 * @author peter
 */
public class StreamApiDetector {
  public static final Set<String> STREAM_INTERMEDIATE_METHODS = ContainerUtil.newHashSet("filter",
                                                                                         "map", "mapToInt", "mapToLong", "mapToDouble",
                                                                                         "flatMap", "flatMapToInt", "flatMapToLong", "flatMapToDouble",
                                                                                         "distinct", "sorted", "peek", "limit", "skip",
                                                                                         "forEach", "forEachOrdered",
                                                                                         "reduce", "collect", "max",
                                                                                         "anyMatch", "allMatch", "noneMatch",
                                                                                         "findFirst", "findAny");

  static boolean isStreamApiCall(LighterAST tree, LighterASTNode methodCall) {
    LighterASTNode methodExpr = tree.getChildren(methodCall).get(0);
    String name = JavaLightTreeUtil.getNameIdentifierText(tree, methodExpr);
    if (STREAM_INTERMEDIATE_METHODS.contains(name)) {
      LighterASTNode qualifier = tree.getChildren(methodExpr).get(0);
      return qualifier.getTokenType() == METHOD_CALL_EXPRESSION && isStreamApiCall(tree, qualifier);
    }
    return isRootCall(tree, methodCall, methodExpr, name);
  }

  private static boolean isRootCall(LighterAST tree, LighterASTNode methodCall, LighterASTNode methodExpr, String name) {
    if ("stream".equals(name) || "parallelStream".equals(name)) {
      List<LighterASTNode> argList = JavaLightTreeUtil.getArgList(tree, methodCall);
      if (argList == null) return false;
      return argList.isEmpty() || !argList.isEmpty() && hasQualifier(tree, methodExpr, "Arrays");
    }
    if ("of".equals(name)) {
      List<LighterASTNode> argList = JavaLightTreeUtil.getArgList(tree, methodCall);
      return argList != null && !argList.isEmpty() && hasQualifier(tree, methodExpr, "Stream");
    }
    return false;
  }

  private static boolean hasQualifier(LighterAST tree, LighterASTNode methodExpr, String refName) {
    LighterASTNode node = tree.getChildren(methodExpr).get(0);
    return node.getTokenType() == REFERENCE_EXPRESSION &&
           refName.equals(JavaLightTreeUtil.getNameIdentifierText(tree, node)) &&
           !hasContextVariable(tree, node, refName);
  }

  private static boolean hasContextVariable(final LighterAST tree, LighterASTNode node, final String varName) {
    final AtomicBoolean result = new AtomicBoolean(false);
    new RecursiveLighterASTNodeWalkingVisitor(tree) {
      @Override
      public void visitNode(@NotNull LighterASTNode element) {
        if (element.getTokenType() == LOCAL_VARIABLE && varName.equals(JavaLightTreeUtil.getNameIdentifierText(tree, element))) {
          result.set(true);
        }
        super.visitNode(element);
      }
    }.visitNode(tree.getRoot());
    return result.get();
  }
}
