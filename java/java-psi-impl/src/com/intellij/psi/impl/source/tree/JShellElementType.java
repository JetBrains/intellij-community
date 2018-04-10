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
package com.intellij.psi.impl.source.tree;

import com.intellij.lang.ASTNode;
import com.intellij.lang.java.JShellLanguage;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.PsiJShellHolderMethodImpl;
import com.intellij.psi.impl.source.PsiJShellImportHolderImpl;
import com.intellij.psi.impl.source.PsiJShellRootClassImpl;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Eugene Zhuravlev
 */
public interface JShellElementType {
  IFileElementType FILE = new IFileElementType("JSHELL_FILE", JShellLanguage.INSTANCE);

  IElementType ROOT_CLASS = new IJShellElementType("JSHELL_ROOT_CLASS") {
    private final AtomicInteger ourClassCounter = new AtomicInteger();
    @Override
    public PsiElement createPsi(ASTNode node) {
      return new PsiJShellRootClassImpl(node, ourClassCounter.getAndIncrement());
    }
  };

  IElementType STATEMENTS_HOLDER = new IJShellElementType("JSHELL_STATEMENTS_HOLDER") {
    private final AtomicInteger ourMethodCounter = new AtomicInteger();
    @Override
    public PsiElement createPsi(ASTNode node) {
      return new PsiJShellHolderMethodImpl(node, ourMethodCounter.getAndIncrement());
    }
  };

  IElementType IMPORT_HOLDER = new IJShellElementType("JSHELL_IMPORT_HOLDER") {
    @Override
    public PsiElement createPsi(ASTNode node) {
      return new PsiJShellImportHolderImpl(node);
    }
  };
}
