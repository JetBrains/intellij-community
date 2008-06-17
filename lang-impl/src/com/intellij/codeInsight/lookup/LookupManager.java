package com.intellij.codeInsight.lookup;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NonNls;
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

  @Deprecated
  public abstract Lookup showLookup(Editor editor, LookupItem[] items, LookupItemPreferencePolicy itemPreferencePolicy);
  public abstract Lookup showLookup(Editor editor, LookupItem[] items, LookupItemPreferencePolicy itemPreferencePolicy, @Nullable String bottomText);
  public abstract void hideActiveLookup();
  public abstract Lookup getActiveLookup();

  @NonNls public static final String PROP_ACTIVE_LOOKUP = "activeLookup";

  public abstract void addPropertyChangeListener(PropertyChangeListener listener);
  public abstract void removePropertyChangeListener(PropertyChangeListener listener);

  public abstract boolean isDisposed();

  public abstract Lookup createLookup(Editor editor, LookupItem[] items, final String prefix, LookupItemPreferencePolicy itemPreferencePolicy,
                                      String bottomText);
}