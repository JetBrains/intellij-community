/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.psi.tree;

import com.intellij.lang.LighterASTNode;
import com.intellij.lang.LighterLazyParseableNode;
import com.intellij.util.diff.FlyweightCapableTreeStructure;
import org.jetbrains.annotations.NotNull;

/**
 * Provides more flexible parsing capabilities for ILazyParseableElementType.
 * One of many benefits is ability to preserve Smart Pointers during reparsing in contrast
 * to ILazyParseableElementType.
 * <p>
 * todo: merge with ILazyParseableElementType
 */
public interface ILightLazyParseableElementType {
  /**
   * Parses the contents of the specified chameleon node and returns the lightweight tree
   * representing the parsed contents.
   *
   * @param chameleon the node to parse.
   * @return the parsed contents of the node.
   */
  @NotNull
  FlyweightCapableTreeStructure<LighterASTNode> parseContents(@NotNull LighterLazyParseableNode chameleon);
}
