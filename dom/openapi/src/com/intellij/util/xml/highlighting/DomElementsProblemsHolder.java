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
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.reflect.DomCollectionChildDescription;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface DomElementsProblemsHolder extends Iterable<DomElementProblemDescriptor>{

  void createProblem(DomElement domElement, @Nullable String message);

  void createProblem(DomElement domElement, DomCollectionChildDescription childDescription, @Nullable String message);

  void createProblem(DomElement domElement, HighlightSeverity highlightType, String message);

  List<DomElementProblemDescriptor> getProblems(DomElement domElement);

  List<DomElementProblemDescriptor> getProblems(DomElement domElement, boolean includeXmlProblems);

  List<DomElementProblemDescriptor> getProblems(DomElement domElement, boolean includeXmlProblems, boolean withChildren);

  List<DomElementProblemDescriptor> getProblems(DomElement domElement,
                                                final boolean includeXmlProblems,
                                                final boolean withChildren,
                                                HighlightSeverity minSeverity);

  List<DomElementProblemDescriptor> getAllProblems();

  HighlightSeverity getDefaultHighlightSeverity();

  void setDefaultHighlightSeverity(final HighlightSeverity defaultHighlightSeverity);
}
