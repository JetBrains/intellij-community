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
package org.intellij.lang.regexp.psi.impl;

import com.intellij.lang.ASTNode;
import org.intellij.lang.regexp.RegExpTT;
import org.intellij.lang.regexp.psi.RegExpElementVisitor;
import org.intellij.lang.regexp.psi.RegExpNamedCharacter;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class RegExpNamedCharacterImpl extends RegExpElementImpl implements RegExpNamedCharacter {
  public RegExpNamedCharacterImpl(ASTNode node) {
    super(node);
  }

  @Nullable
  @Override
  public ASTNode getNameNode() {
    return getNode().findChildByType(RegExpTT.NAME);
  }

  @Override
  public String getName() {
    final ASTNode node = getNameNode();
    return (node == null) ? null : node.getText();
  }

  @Override
  public void accept(RegExpElementVisitor visitor) {
    visitor.visitRegExpNamedCharacter(this);
  }
}
