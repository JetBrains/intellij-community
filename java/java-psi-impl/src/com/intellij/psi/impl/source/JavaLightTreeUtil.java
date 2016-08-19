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
package com.intellij.psi.impl.source;

import com.intellij.lang.LighterAST;
import com.intellij.lang.LighterASTNode;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.impl.cache.RecordUtil;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.LightTreeUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.intellij.psi.impl.source.tree.JavaElementType.ANONYMOUS_CLASS;
import static com.intellij.psi.impl.source.tree.JavaElementType.EXPRESSION_LIST;

/**
 * @author peter
 */
public class JavaLightTreeUtil {
  @Nullable
  @Contract("_,null->null")
  public static List<LighterASTNode> getArgList(@NotNull LighterAST tree, @Nullable LighterASTNode call) {
    LighterASTNode anonClass = LightTreeUtil.firstChildOfType(tree, call, ANONYMOUS_CLASS);
    LighterASTNode exprList = LightTreeUtil.firstChildOfType(tree, anonClass != null ? anonClass : call, EXPRESSION_LIST);
    return exprList == null ? null : LightTreeUtil.getChildrenOfType(tree, exprList, ElementType.EXPRESSION_BIT_SET);
  }

  @Nullable
  @Contract("_,null->null")
  public static String getNameIdentifierText(@NotNull LighterAST tree, @Nullable LighterASTNode idOwner) {
    LighterASTNode id = LightTreeUtil.firstChildOfType(tree, idOwner, JavaTokenType.IDENTIFIER);
    return id != null ? RecordUtil.intern(tree.getCharTable(), id) : null;
  }
}
