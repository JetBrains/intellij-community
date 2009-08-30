package com.intellij.packaging.impl.compiler;

import com.intellij.packaging.artifacts.Artifact;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * @author nik
 */
public interface ArtifactAwareCompiler {

  boolean shouldRun(@NotNull Collection<? extends Artifact> changedArtifacts);

}
