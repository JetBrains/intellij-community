package org.jetbrains.jps.incremental.artifacts.impl;

import com.intellij.util.Processor;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.artifact.elements.JpsComplexPackagingElement;
import org.jetbrains.jps.model.artifact.elements.JpsCompositePackagingElement;
import org.jetbrains.jps.model.artifact.elements.JpsPackagingElement;

import java.util.Set;

/**
 * @author nik
 */
public class JpsArtifactUtil {
  public static boolean processPackagingElements(@NotNull JpsPackagingElement element,
                                                 @NotNull Processor<JpsPackagingElement> processor) {
    return processPackagingElements(element, processor, new HashSet<JpsPackagingElement>());
  }

  private static boolean processPackagingElements(@NotNull JpsPackagingElement element,
                                                 @NotNull Processor<JpsPackagingElement> processor,
                                                 final Set<JpsPackagingElement> processed) {
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
}
