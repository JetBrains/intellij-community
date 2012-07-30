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

import com.intellij.psi.codeStyle.arrangement.match.ByNameArrangementEntryMatcher;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;
import java.util.Set;

/**
 * @author Denis Zhdanov
 * @since 7/19/12 6:40 PM
 */
public class ByNameArrangementEntryMatcherSerializer implements ArrangementEntryMatcherSerializer<ByNameArrangementEntryMatcher> {

  @NotNull
  @Override
  public Class<ByNameArrangementEntryMatcher> getMatcherClass() {
    return ByNameArrangementEntryMatcher.class;
  }

  @NotNull
  @Override
  public Set<ArrangementEntryMatcherSerializationTag> getTags() {
    return EnumSet.of(ArrangementEntryMatcherSerializationTag.NAME);
  }

  @NotNull
  @Override
  public Element serialize(@NotNull ByNameArrangementEntryMatcher matcher) {
    return new Element(ArrangementEntryMatcherSerializationTag.NAME.toString()).setText(matcher.getPattern());
  }

  @NotNull
  @Override
  public ByNameArrangementEntryMatcher deserialize(@NotNull Element element) throws IllegalArgumentException {
    return new ByNameArrangementEntryMatcher(element.getText());
  }
}
