// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler;

import com.intellij.openapi.compiler.CompilerMessage;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.compiler.JavaCompilerBundle;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.util.TripleFunction;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

public final class CompilerMessageImpl implements CompilerMessage {

  private final Project myProject;
  private final CompilerMessageCategory myCategory;
  private @Nullable Navigatable myNavigatable;
  private final @Nls(capitalization = Nls.Capitalization.Sentence) String myMessage;
  private final VirtualFile myFile;
  private final int myRow;
  private final int myColumn;
  private final Collection<String> myModuleNames;
  private @NotNull TripleFunction<? super CompilerMessage, ? super Integer, ? super Integer, Integer> myColumnAdjuster = (msg, line, col) -> col;

  public CompilerMessageImpl(Project project, CompilerMessageCategory category, @Nls(capitalization = Nls.Capitalization.Sentence) String message) {
    this(project, category, message, null, -1, -1, null);
  }

  public CompilerMessageImpl(Project project,
                             @NotNull CompilerMessageCategory category,
                             @Nls(capitalization = Nls.Capitalization.Sentence) String message,
                             final @Nullable VirtualFile file,
                             int row,
                             int column,
                             final @Nullable Navigatable navigatable) {
    this(project, category, message, file, row, column, navigatable, Collections.emptyList());
  }

  public CompilerMessageImpl(Project project,
                             @NotNull CompilerMessageCategory category, @Nls(capitalization = Nls.Capitalization.Sentence) String message,
                             final @Nullable VirtualFile file, int row, int column, final @Nullable Navigatable navigatable, @NotNull Collection<String> moduleNames) {
    myProject = project;
    myCategory = category;
    myNavigatable = navigatable;
    myMessage = message == null ? "" : message;
    myRow = row;
    myColumn = column;
    myFile = file;
    myModuleNames = Collections.unmodifiableCollection(moduleNames);
  }

  public void setColumnAdjuster(@NotNull TripleFunction<? super CompilerMessage, ? super Integer, ? super Integer, Integer> columnAdjuster) {
    myColumnAdjuster = columnAdjuster;
  }

  @Override
  public @NotNull CompilerMessageCategory getCategory() {
    return myCategory;
  }

  @Override
  public String getMessage() {
    return myMessage;
  }

  @Override
  public Navigatable getNavigatable() {
    if (myNavigatable != null) {
      return myNavigatable;
    }
    final VirtualFile virtualFile = getVirtualFile();
    if (virtualFile != null && virtualFile.isValid() && !virtualFile.getFileType().isBinary()) {
      final int line = getLine() - 1; // editor lines are zero-based
      if (line >= 0) {
        return myNavigatable = new OpenFileDescriptor(myProject, virtualFile, line, myColumnAdjuster.fun(this, line, Math.max(0, getColumn()-1))) ;
      }
    }
    return null;
  }

  @Override
  public VirtualFile getVirtualFile() {
    return myFile;
  }

  @Override
  public String getExportTextPrefix() {
    if (getLine() >= 0) {
      return JavaCompilerBundle.message("compiler.results.export.text.prefix", getLine());
    }
    return "";
  }

  @Override
  public String getRenderTextPrefix() {
    if (getLine() >= 0) {
      return "(" + getLine() + ", " + getColumn() + ")";
    }
    return "";
  }

  public int getLine() {
    return myRow;
  }

  public int getColumn() {
    return myColumn;
  }

  @Override
  public Collection<String> getModuleNames() {
    return myModuleNames;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof CompilerMessage)) return false;

    final CompilerMessageImpl compilerMessage = (CompilerMessageImpl)o;

    if (myColumn != compilerMessage.myColumn) return false;
    if (myRow != compilerMessage.myRow) return false;
    if (!myCategory.equals(compilerMessage.myCategory)) return false;
    if (myFile != null ? !myFile.equals(compilerMessage.myFile) : compilerMessage.myFile != null) return false;
    if (!myMessage.equals(compilerMessage.myMessage)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result;
    result = myCategory.hashCode();
    result = 29 * result + myMessage.hashCode();
    result = 29 * result + (myFile != null ? myFile.hashCode() : 0);
    result = 29 * result + myRow;
    result = 29 * result + myColumn;
    return result;
  }

  @Override
  public String toString() {
    return myMessage;
  }
}
