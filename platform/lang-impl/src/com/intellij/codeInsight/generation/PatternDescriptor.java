package com.intellij.codeInsight.generation;

import com.intellij.codeInsight.template.Template;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.util.NlsActions;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
* @author Dmitry Avdeev
*/
public abstract class PatternDescriptor {

  public static final String ROOT = "root";

  @Nullable
  public @NonNls String getId() {
    return null;
  }

  @NotNull
  public abstract String getParentId();

  @NotNull
  public abstract @NlsActions.ActionText String getName();

  @Nullable
  public abstract Icon getIcon();

  @Nullable
  public abstract Template getTemplate();

  public abstract void actionPerformed(DataContext context);
}
