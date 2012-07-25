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
 * Identifies a strategy that can tweak default {@link ArrangementRule arrangement rules} (de)serialization mechanism.
 * <p/>
 * Implementations of this interface are expected to be thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 7/18/12 10:30 AM
 */
public interface ArrangementRuleSerializer {

  /**
   * Allows to provide custom rule deserialization logic. This method is expected to be consistent with {@link #serialize(ArrangementRule)}.
   * <p/>
   * <b>Note:</b> it's save to return <code>null</code> if current rearranger doesn't use custom rules (rules over those
   * located at the <code>'lang-api'</code>/<code>'lang-impl'</code> modules).
   *
   * @param element  serialized rule
   * @return         rule de-serialized from the given element;
   *                 <code>null</code> as an indication that current rearranger doesn't provide custom serialization logic
   *                 for the target rule serialized at the given element, i.e. default deserialization algorithm should be used for it
   */
  @Nullable
  ArrangementRule deserialize(@NotNull Element element);

  /**
   * Allows to provide custom rule serialization logic. This method is expected to be consistent with {@link #deserialize(Element)}.
   * <p/>
   * <b>Note:</b> it's save to return <code>null</code> if current rearranger doesn't use custom rules (rules over those
   * located at the <code>'lang-api'</code>/<code>'lang-impl'</code> modules).
   *
   * @param rule  rule to serialize
   * @return      serialized rule;
   *              <code>null</code> as an indication that no custom serialization logic for the given rule
   *              is provided by the current rearranger
   */
  @Nullable
  Element serialize(ArrangementRule rule);
}
