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
package com.intellij.psi.impl.source;

import com.intellij.ide.highlighter.JShellFileType;
import com.intellij.lang.Language;
import com.intellij.lang.java.JShellLanguage;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.JShellElementType;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 *         Date: 28-Jul-15
 */
public class JShellFileImpl extends PsiJavaFileBaseImpl implements PsiJShellFile {
  public JShellFileImpl(FileViewProvider viewProvider) {
    super(JShellElementType.FILE, JShellElementType.FILE, viewProvider);
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor, @NotNull ResolveState state, PsiElement lastParent, @NotNull PsiElement place) {
    return super.processDeclarations(processor, state, lastParent, place);
  }

  @NotNull
  @Override
  public Language getLanguage() {
    return JShellLanguage.INSTANCE;
  }

  @NotNull
  public FileType getFileType() {
    return JShellFileType.INSTANCE;
  }

  public boolean isPhysical() {
    return getViewProvider().isPhysical();
  }

  @Override
  public Collection<PsiElement> getExecutableSnippets() {
    final List<PsiElement> result = new SmartList<>();
    collectExecutableSnippets(this, result);
    return result;
  }

  private static void collectExecutableSnippets(PsiElement container, Collection<PsiElement> result) {
    for (PsiElement child = container.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (child instanceof PsiJShellRootClass) {
        collectExecutableSnippets(child, result);
      }
      else {
        if (isExecutable(child)) {
          result.add(child);
        }
      }
    }
  }

  private static final Condition<PsiElement> EXECUTABLE_PREDICATE = elem -> elem != null && !(elem instanceof PsiWhiteSpace || elem instanceof PsiEmptyStatement || elem instanceof PsiComment);
  private static boolean isExecutable(PsiElement element) {
    if (element instanceof PsiJShellHolderMethod) {
      for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
        if (EXECUTABLE_PREDICATE.value(child)) {
          return true;
        }
      }
      return false;
    }
    return EXECUTABLE_PREDICATE.value(element);
  }
}
