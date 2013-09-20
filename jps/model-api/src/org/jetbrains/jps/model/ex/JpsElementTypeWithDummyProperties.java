/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.jps.model.ex;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsDummyElement;
import org.jetbrains.jps.model.JpsElementFactory;
import org.jetbrains.jps.model.JpsElementTypeWithDefaultProperties;

/**
 * A base class for type elements without any specific properties
 *
 * @author nik
 */
public abstract class JpsElementTypeWithDummyProperties extends JpsElementTypeBase<JpsDummyElement> implements JpsElementTypeWithDefaultProperties<JpsDummyElement> {
  @NotNull
  @Override
  public JpsDummyElement createDefaultProperties() {
    return JpsElementFactory.getInstance().createDummyElement();
  }
}
