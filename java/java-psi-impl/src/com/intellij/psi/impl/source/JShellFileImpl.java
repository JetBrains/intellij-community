// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source;

import com.intellij.ide.highlighter.JShellFileType;
import com.intellij.lang.Language;
import com.intellij.lang.java.JShellLanguage;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.JShellElementType;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 */
public class JShellFileImpl extends PsiJavaFileBaseImpl implements PsiJShellFile {
  public JShellFileImpl(FileViewProvider viewProvider) {
    super(JShellElementType.FILE, JShellElementType.FILE, viewProvider);
  }

  @NotNull
  @Override
  public Language getLanguage() {
    return JShellLanguage.INSTANCE;
  }

  @Override
  @NotNull
  public FileType getFileType() {
    return JShellFileType.INSTANCE;
  }

  @Override
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
