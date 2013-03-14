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
package com.intellij.psi.codeStyle.arrangement;

import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Identifies a strategy that can tweak default {@link ArrangementSettings arrangement settings} (de)serialization mechanism.
 * <p/>
 * Implementations of this interface are expected to be thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 7/18/12 10:30 AM
 */
public interface ArrangementSettingsSerializer {

  /**
   * Allows to provide custom settings deserialization logic. This method is expected to be consistent
   * with {@link #serialize(ArrangementSettings, Element)}.
   * <p/>
   * <b>Note:</b> it's save to return <code>null</code> if current rearranger doesn't use custom settings (settings over those
   * located at the <code>'lang-api'</code>/<code>'lang-impl'</code> modules).
   *
   * @param element  serialized settings holder
   * @return         settings de-serialized from the given element
   */
  @Nullable
  ArrangementSettings deserialize(@NotNull Element element);

  /**
   * Allows to provide custom settings serialization logic. This method is expected to be consistent with {@link #deserialize(Element)}.
   * <p/>
   * <b>Note:</b> it's save to return <code>null</code> if current rearranger doesn't use custom settings (settings over those
   * located at the <code>'lang-api'</code>/<code>'lang-impl'</code> modules).
   *
   * @param settings  settings to serialize
   * @param holder    element to hold serialized settings
   */
  void serialize(@NotNull ArrangementSettings settings, @NotNull Element holder);
}
