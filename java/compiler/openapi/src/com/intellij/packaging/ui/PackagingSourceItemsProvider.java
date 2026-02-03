// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.packaging.ui;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.packaging.artifacts.Artifact;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * Override this class to provide additional nodes in 'Available Elements' tree on 'Output Layout' tab of an artifact editor. This tree
 * contains elements which are usually included into an artifact so don't include optional and rarely used items there. All packaging elements
 * may be added by clicking on '+' icon on the toolbar above 'Output Layout' tree anyway.
 *
 * <p/>
 * The implementation should be registered in plugin.xml file:
 * <pre>
 * &lt;extensions defaultExtensionNs="com.intellij"&gt;
 * &nbsp;&nbsp;&lt;packaging.sourceItemProvider implementation="qualified-class-name"/&gt;
 * &lt;/extensions&gt;
 * </pre>
 */
public abstract class PackagingSourceItemsProvider {
  public static final ExtensionPointName<PackagingSourceItemsProvider> EP_NAME = ExtensionPointName.create("com.intellij.packaging.sourceItemProvider");

  /**
   * Return items which should be shown be shown under {@code parent} node in 'Available Elements' tree for {@code artifact}.
   */
  public abstract @NotNull Collection<? extends PackagingSourceItem> getSourceItems(@NotNull ArtifactEditorContext editorContext, @NotNull Artifact artifact,
                                                                           @Nullable PackagingSourceItem parent);
}
