/*
 * Copyright 2000-2006 JetBrains s.r.o.
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
 *
 */

package com.intellij.util.xml.highlighting;

import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.project.Project;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomFileElement;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public abstract class DomElementAnnotationsManager {

  public static DomElementAnnotationsManager getInstance(Project project) {
    return project.getComponent(DomElementAnnotationsManager.class);
  }

  @NotNull
  public abstract DomElementsProblemsHolder getProblemHolder(DomElement element);
  @NotNull
  public abstract DomElementsProblemsHolder getCachedProblemHolder(DomElement element);

  public abstract List<DomElementProblemDescriptor> getAllProblems(final DomFileElement<?> fileElement, HighlightSeverity minSeverity);

  public abstract void registerDomElementsAnnotator(DomElementsAnnotator annotator, Class aClass);
}
