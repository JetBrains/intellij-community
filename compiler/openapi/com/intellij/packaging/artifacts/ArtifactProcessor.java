package com.intellij.packaging.artifacts;

import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.options.UnnamedConfigurable;

/**
 * @author nik
 */
public abstract class ArtifactProcessor<S> implements PersistentStateComponent<S> {
  public abstract void buildStarted();

  public abstract void buildFinished();

  public abstract UnnamedConfigurable createConfigurable();
}
