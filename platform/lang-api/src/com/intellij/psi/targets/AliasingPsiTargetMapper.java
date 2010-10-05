package com.intellij.psi.targets;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.pom.PomTarget;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public interface AliasingPsiTargetMapper {
  ExtensionPointName<AliasingPsiTargetMapper> EP_NAME = ExtensionPointName.create("com.intellij.aliasingPsiTargetMapper");

  @NotNull
  Set<AliasingPsiTarget> getTargets(@NotNull PomTarget target);
}
