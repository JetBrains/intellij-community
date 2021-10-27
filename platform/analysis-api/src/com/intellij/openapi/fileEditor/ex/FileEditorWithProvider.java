// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.ex;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import org.jetbrains.annotations.NotNull;

/**
 * A holder for both {@link FileEditor} and {@link FileEditorProvider}
 * The package is suffixed with 'ex' for backward compatibility
 */
public class FileEditorWithProvider {
  private final FileEditor myFileEditor;
  private final FileEditorProvider myProvider;

  public FileEditorWithProvider(@NotNull FileEditor fileEditor, @NotNull FileEditorProvider provider) {
    myFileEditor = fileEditor;
    myProvider = provider;
  }

  public @NotNull FileEditor getFileEditor() {
    return myFileEditor;
  }

  public @NotNull FileEditorProvider getProvider() {
    return myProvider;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof FileEditorWithProvider)) return false;

    FileEditorWithProvider provider = (FileEditorWithProvider)o;

    if (!myFileEditor.equals(provider.myFileEditor)) return false;
    if (!myProvider.equals(provider.myProvider)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myFileEditor.hashCode();
    result = 31 * result + myProvider.hashCode();
    return result;
  }

  // Kotlin interop
  @NotNull
  public FileEditor component1() {
    return myFileEditor;
  }

  @NotNull
  public FileEditorProvider component2() {
    return myProvider;
  }
}
