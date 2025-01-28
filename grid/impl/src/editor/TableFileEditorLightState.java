package com.intellij.database.editor;

import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileEditor.FileEditorStateLevel;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;

public class TableFileEditorLightState implements FileEditorState, Serializable {
  @Attribute("transposed")
  public boolean transposed = false;

  @Override
  public boolean canBeMergedWith(@NotNull FileEditorState otherState,
                                 @NotNull FileEditorStateLevel level) {
    return false;
  }
}
