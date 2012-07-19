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
package com.intellij.psi.codeStyle.arrangement.match.serialization;

import com.intellij.psi.codeStyle.arrangement.match.ArrangementEntryMatcher;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Strategy that knows how to serialize {@link ArrangementEntryMatcher} objects of particular class.
 * <p/>
 * Implementations of this interface are expected to be thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 7/19/12 12:31 PM
 */
public interface ArrangementEntryMatcherSerializer<T extends ArrangementEntryMatcher> {

  /**
   * @return    class of the target matcher that can be processed by the current serializer
   */
  @NotNull
  Class<T> getMatcherClass();

  /**
   * @return    allowed element names of the serialized matcher. Are expected to be unique between
   *            {@link ArrangementEntryMatcherSerializer} implementations 
   */
  @NotNull
  Set<ArrangementEntryMatcherSerializationTag> getTags();

  @NotNull
  Element serialize(@NotNull T matcher);

  /**
   * Allows to deserialize target matcher object serialized previously via {@link #serialize(ArrangementEntryMatcher)}
   * 
   * @param element  serialized representation of the target matcher
   * @return         de-serialized matcher instance
   * @throws IllegalArgumentException   if current strategy doesn't know how to deserialize target matcher from the given
   *                                    element  (the exception must not be thrown if given element's name is in
   *                                    {@link #getTags() target element names})
   */
  @NotNull
  T deserialize(@NotNull Element element) throws IllegalArgumentException;
}
