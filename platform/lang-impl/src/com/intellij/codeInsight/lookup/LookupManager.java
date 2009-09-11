package com.intellij.codeInsight.lookup;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.beans.PropertyChangeListener;

public abstract class LookupManager {
  public static LookupManager getInstance(Project project){
    return project.getComponent(LookupManager.class);
  }

  @Nullable
  public static Lookup getActiveLookup(Editor editor) {
    final Project project = editor.getProject();
    if (project == null) return null;

    return getInstance(project).getActiveLookup();
  }

  public Lookup showLookup(Editor editor, @NotNull LookupElement... items) {
    return showLookup(editor, items, "", LookupArranger.DEFAULT);
  }

  public Lookup showLookup(Editor editor, @NotNull LookupElement[] items, String prefix) {
    return showLookup(editor, items, prefix, LookupArranger.DEFAULT);
  }

  public abstract Lookup showLookup(Editor editor, @NotNull LookupElement[] items, String prefix, @NotNull LookupArranger arranger);

  public abstract void hideActiveLookup();
  public abstract Lookup getActiveLookup();

  @NonNls public static final String PROP_ACTIVE_LOOKUP = "activeLookup";

  public abstract void addPropertyChangeListener(PropertyChangeListener listener);
  public abstract void removePropertyChangeListener(PropertyChangeListener listener);

  public abstract boolean isDisposed();

  public abstract Lookup createLookup(Editor editor, @NotNull LookupElement[] items, final String prefix, LookupArranger arranger);

}