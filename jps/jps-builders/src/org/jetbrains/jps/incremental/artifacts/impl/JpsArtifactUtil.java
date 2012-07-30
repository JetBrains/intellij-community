package org.jetbrains.jps.incremental.artifacts.impl;

import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.artifact.elements.JpsComplexPackagingElement;
import org.jetbrains.jps.model.artifact.elements.JpsCompositePackagingElement;
import org.jetbrains.jps.model.artifact.elements.JpsPackagingElement;

/**
 * @author nik
 */
public class JpsArtifactUtil {
  public static boolean processPackagingElements(@NotNull JpsPackagingElement element, @NotNull Processor<JpsPackagingElement> processor) {
    if (!processor.process(element)) {
      return false;
    }

    if (element instanceof JpsCompositePackagingElement) {
      for (JpsPackagingElement child : ((JpsCompositePackagingElement)element).getChildren()) {
        processPackagingElements(child, processor);
      }
    }
    else if (element instanceof JpsComplexPackagingElement) {
      for (JpsPackagingElement child : ((JpsComplexPackagingElement)element).getSubstitution()) {
        processPackagingElements(child, processor);
      }
    }
    return true;
  }
}
