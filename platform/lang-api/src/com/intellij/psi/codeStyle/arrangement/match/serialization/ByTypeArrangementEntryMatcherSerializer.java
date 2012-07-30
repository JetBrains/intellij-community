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

import com.intellij.psi.codeStyle.arrangement.match.ArrangementEntryType;
import com.intellij.psi.codeStyle.arrangement.match.ByTypeArrangementEntryMatcher;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;
import java.util.Set;

/**
 * @author Denis Zhdanov
 * @since 7/19/12 12:44 PM
 */
public class ByTypeArrangementEntryMatcherSerializer implements ArrangementEntryMatcherSerializer<ByTypeArrangementEntryMatcher> {
  
  private static final String SEPARATOR = "|";
  
  @NotNull
  @Override
  public Class<ByTypeArrangementEntryMatcher> getMatcherClass() {
    return ByTypeArrangementEntryMatcher.class;
  }

  @NotNull
  @Override
  public Set<ArrangementEntryMatcherSerializationTag> getTags() {
    return EnumSet.of(ArrangementEntryMatcherSerializationTag.TYPE);
  }

  @NotNull
  @Override
  public Element serialize(@NotNull ByTypeArrangementEntryMatcher matcher) {
    StringBuilder buffer = new StringBuilder();
    for (ArrangementEntryType type : matcher.getTypes()) {
      buffer.append(type).append(SEPARATOR);
    }
    if (buffer.length() > 0) {
      buffer.setLength(buffer.length() - 1);
    }
    return new Element(ArrangementEntryMatcherSerializationTag.TYPE.toString()).setText(buffer.toString());
  }

  @NotNull
  @Override
  public ByTypeArrangementEntryMatcher deserialize(@NotNull Element element) throws IllegalArgumentException {
    if (!ArrangementEntryMatcherSerializationTag.TYPE.toString().equals(element.getName())) {
      throw new IllegalArgumentException(String.format(
        "Can't deserialize matcher of class %s from an element with name '%s'. Expected name: '%s'",
        getMatcherClass(), element.getName(), ArrangementEntryMatcherSerializationTag.TYPE.toString()));
    }
    
    String text = element.getText();
    if (text == null) {
      throw new IllegalArgumentException(String.format(
        "Can't deserialize matcher of class %s. Reason: no information about target type(s) is found", getMatcherClass()
      ));
    }

    Set<ArrangementEntryType> types = EnumSet.noneOf(ArrangementEntryType.class);
    for (String s : text.split("\\" + SEPARATOR)) {
      types.add(ArrangementEntryType.valueOf(s));
    }
    return new ByTypeArrangementEntryMatcher(types);
  }
}
