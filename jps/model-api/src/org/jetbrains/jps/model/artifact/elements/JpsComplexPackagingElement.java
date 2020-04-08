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
package org.jetbrains.jps.model.artifact.elements;

import java.util.List;

/**
 * Represents a synthetic node which is substituted by a collection of other elements when the artifact is being built. E.g. a node which
 * represents a Java library is substituted by {@link JpsFileCopyPackagingElement} for its JAR files.
 */
public interface JpsComplexPackagingElement extends JpsPackagingElement {
  List<JpsPackagingElement> getSubstitution();
}
