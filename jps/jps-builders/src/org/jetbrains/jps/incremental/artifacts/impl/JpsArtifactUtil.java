/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.jps.incremental.artifacts.impl;

import com.intellij.openapi.util.text.StringUtil;
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

  public static boolean isArchiveName(String name) {
    return name.length() >= 4 && name.charAt(name.length() - 4) == '.' && StringUtil.endsWithIgnoreCase(name, "ar");
  }
}
