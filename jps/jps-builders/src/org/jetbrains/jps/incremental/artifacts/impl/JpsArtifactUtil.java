// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.incremental.artifacts.impl;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.artifact.JpsArtifact;
import org.jetbrains.jps.model.artifact.elements.JpsComplexPackagingElement;
import org.jetbrains.jps.model.artifact.elements.JpsCompositePackagingElement;
import org.jetbrains.jps.model.artifact.elements.JpsModuleOutputPackagingElement;
import org.jetbrains.jps.model.artifact.elements.JpsPackagingElement;
import org.jetbrains.jps.model.module.JpsModule;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public final class JpsArtifactUtil {
  public static boolean processPackagingElements(@NotNull JpsPackagingElement element,
                                                 @NotNull Processor<? super JpsPackagingElement> processor) {
    return processPackagingElements(element, processor, new HashSet<>());
  }

  private static boolean processPackagingElements(@NotNull JpsPackagingElement element,
                                                 @NotNull Processor<? super JpsPackagingElement> processor,
                                                 final Set<? super JpsPackagingElement> processed) {
    if (!processed.add(element)) {
      return false;
    }
    if (!processor.process(element)) {
      return false;
    }

    if (element instanceof JpsCompositePackagingElement) {
      for (JpsPackagingElement child : ((JpsCompositePackagingElement)element).getChildren()) {
        processPackagingElements(child, processor, processed);
      }
    }
    else if (element instanceof JpsComplexPackagingElement) {
      for (JpsPackagingElement child : ((JpsComplexPackagingElement)element).getSubstitution()) {
        processPackagingElements(child, processor, processed);
      }
    }
    return true;
  }

  public static boolean isArchiveName(String name) {
    return name.length() >= 4 && name.charAt(name.length() - 4) == '.' && StringUtil.endsWithIgnoreCase(name, "ar");
  }

  public static Set<JpsModule> getModulesIncludedInArtifacts(final @NotNull Collection<? extends JpsArtifact> artifacts) {
    final Set<JpsModule> modules = new THashSet<>();
    for (JpsArtifact artifact : artifacts) {
      processPackagingElements(artifact.getRootElement(), element -> {
        if (element instanceof JpsModuleOutputPackagingElement) {
          ContainerUtil.addIfNotNull(modules, ((JpsModuleOutputPackagingElement)element).getModuleReference().resolve());
        }
        return true;
      });
    }
    return modules;
  }
}
