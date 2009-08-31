package com.intellij.jar;

import com.intellij.openapi.deployment.ContainerElement;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ui.configuration.packaging.PackagingArtifact;
import com.intellij.openapi.roots.ui.configuration.packaging.PackagingTreeBuilder;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ModuleStructureConfigurable;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.Icons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public class JarPackagingTreeBuilder extends PackagingTreeBuilder {
  private final Module myModule;

  public JarPackagingTreeBuilder(final Module module) {
    myModule = module;
  }

  public PackagingArtifact createRootArtifact() {
    return new PackagingArtifact() {
      @NotNull
      @Override
      public String getOutputFileName() {
        return myModule.getName();
      }

      @Override
      public void render(@NotNull final ColoredTreeCellRenderer renderer, final SimpleTextAttributes mainAttributes, final SimpleTextAttributes commentAttributes) {
        renderer.setIcon(Icons.JAR_ICON);
        renderer.append(myModule.getName() + ".jar", mainAttributes);
      }

      @Override
      public void navigate(final ModuleStructureConfigurable configurable, @Nullable final ContainerElement element) {
      }

      @Override
      public String getDisplayName() {
        return myModule.getName();
      }
    };
  }
}
