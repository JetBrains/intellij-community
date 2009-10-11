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

package com.intellij.psi.text;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;

public abstract class BlockSupport {
  public static BlockSupport getInstance(Project project) {
    return ServiceManager.getService(project, BlockSupport.class);
  }

  public abstract void reparseRange(PsiFile file, int startOffset, int endOffset, @NonNls CharSequence newText) throws IncorrectOperationException;
  public abstract void reparseRange(PsiFile file, int startOffset, int endOffset, int lengthShift, CharSequence newText) throws IncorrectOperationException;

  public static final Key<Boolean> DO_NOT_REPARSE_INCREMENTALLY = Key.create("SKIP_INCREMENTAL_REPARSE");
  public static final Key<ASTNode> TREE_TO_BE_REPARSED = new Key<ASTNode>("TREE_TO_BE_REPARSED");

  public static class ReparsedSuccessfullyException extends RuntimeException {
    public synchronized Throwable fillInStackTrace() {
      return this;
    }
  }
}
