/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.psi.impl.source.tree.java;

import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;

/**
 * @author ven
 */
public class AnnotationMethodElement extends MethodElement {
  public AnnotationMethodElement() {
    super(ANNOTATION_METHOD);
  }

  @Override
  public ASTNode findChildByRole(int role) {
    if (role == ChildRole.ANNOTATION_DEFAULT_VALUE) {
      return findChildByType(ANNOTATION_MEMBER_VALUE_BIT_SET);
    }

    return super.findChildByRole(role);
  }

  @Override
  public int getChildRole(@NotNull ASTNode child) {
    if (ANNOTATION_MEMBER_VALUE_BIT_SET.contains(child.getElementType())) {
      return ChildRole.ANNOTATION_DEFAULT_VALUE;
    }

    return super.getChildRole(child);
  }
}
