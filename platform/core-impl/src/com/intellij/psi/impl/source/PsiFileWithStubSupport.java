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

/*
 * @author max
 */
package com.intellij.psi.impl.source;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubTree;
import org.jetbrains.annotations.Nullable;

/**
 * A base interface for PSI files that may contain not only text-based syntactic trees as their content,
 * but also a more lightweight representation called stubs.
 * @see com.intellij.extapi.psi.StubBasedPsiElementBase
 */
public interface PsiFileWithStubSupport extends PsiFile {
  /**
   * @return the stub tree for this file, if it's stub-based at all. Will be null after the AST has been loaded
   * (e.g. by calling {@link PsiElement#getNode()} or {@link PsiElement#getText()}.
   */
  @Nullable
  StubTree getStubTree();

  /**
   * Loads the AST for this file and returns the node which corresponds to the given stub
   */
  @Nullable
  ASTNode findTreeForStub(StubTree tree, StubElement<?> stub);
}