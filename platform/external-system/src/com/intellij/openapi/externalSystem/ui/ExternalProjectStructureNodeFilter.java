package com.intellij.openapi.externalSystem.ui;

import org.jetbrains.annotations.NotNull;

/**
 * @author Denis Zhdanov
 * @since 3/11/12 11:40 AM
 */
public interface ExternalProjectStructureNodeFilter {

  boolean isVisible(@NotNull ProjectStructureNode<?> node);
}
