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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.codeStyle.arrangement.ArrangementOperator;
import com.intellij.psi.codeStyle.arrangement.match.ArrangementEntryMatcher;
import com.intellij.psi.codeStyle.arrangement.match.CompositeArrangementEntryMatcher;
import com.intellij.util.containers.ClassMap;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Denis Zhdanov
 * @since 7/19/12 1:00 PM
 */
public class DefaultArrangementEntryMatcherSerializer {

  private static final Logger LOG = Logger.getInstance("#" + DefaultArrangementEntryMatcherSerializer.class.getName());

  @NotNull private final ClassMap<ArrangementEntryMatcherSerializer<?>> mySerializers
    = new ClassMap<ArrangementEntryMatcherSerializer<?>>();

  @NotNull private final Map<String /* element name */, ArrangementEntryMatcherSerializer<?>> myDeserializers =
    new HashMap<String, ArrangementEntryMatcherSerializer<?>>();

  public DefaultArrangementEntryMatcherSerializer() {
    register(new ByTypeArrangementEntryMatcherSerializer());
    register(new ByNameArrangementEntryMatcherSerializer());
    register(new CompositeSerializer());
  }

  private void register(@NotNull ArrangementEntryMatcherSerializer<?> serializer) {
    mySerializers.put(serializer.getMatcherClass(), serializer);
    for (ArrangementEntryMatcherSerializationTag tag : serializer.getTags()) {
      ArrangementEntryMatcherSerializer<?> old = myDeserializers.put(tag.toString(), serializer);
      assert old == null : String.format("Multiple serializers are registered for the tag %s: %s and %s", tag.toString(), old, serializer);
    }
  }

  @SuppressWarnings("unchecked")
  @Nullable
  public <T extends ArrangementEntryMatcher> Element serialize(@NotNull T matcher) {
    ArrangementEntryMatcherSerializer<T> serializer = (ArrangementEntryMatcherSerializer<T>)mySerializers.get(matcher.getClass());
    if (serializer != null) {
      return serializer.serialize(matcher);
    }
    StringBuilder buffer = new StringBuilder();
    for (ArrangementEntryMatcherSerializer<?> s : mySerializers.values()) {
      buffer.append(s.getMatcherClass()).append(", ");
    }
    if (buffer.length() > 0) {
      buffer.setLength(buffer.length() - 2);
    }
    LOG.warn(String.format(
      "Can't serialize arrangement entry matcher of class '%s'. Reason: no corresponding serializer is registered."
      + "Available serializers can handle the following matchers: '%s'", matcher.getClass(), buffer.toString() 
      ));
    return null;
  }

  @SuppressWarnings("unchecked")
  @Nullable
  public <T extends ArrangementEntryMatcher> T deserialize(@NotNull Element matcherElement) {
    ArrangementEntryMatcherSerializer<T> serializer = (ArrangementEntryMatcherSerializer<T>)myDeserializers.get(matcherElement.getName());
    if (serializer != null) {
      return serializer.deserialize(matcherElement);
    }
    StringBuilder buffer = new StringBuilder();
    for (String s : myDeserializers.keySet()) {
      buffer.append(s).append(", ");
    }
    if (buffer.length() > 0) {
      buffer.setLength(buffer.length() - 2);
    }
    LOG.warn(String.format(
      "Can't deserialize an arrangement entry matcher from matchElement with name '%s'. Reason: only the following elements are supported: %s",
      matcherElement.getName(), buffer
    ));
    return null;
  }
  
  private class CompositeSerializer implements ArrangementEntryMatcherSerializer<CompositeArrangementEntryMatcher> {
    @NotNull
    @Override
    public Class<CompositeArrangementEntryMatcher> getMatcherClass() {
      return CompositeArrangementEntryMatcher.class;
    }

    @NotNull
    @Override
    public Set<ArrangementEntryMatcherSerializationTag> getTags() {
      return EnumSet.of(ArrangementEntryMatcherSerializationTag.AND, ArrangementEntryMatcherSerializationTag.OR);
    }

    @NotNull
    @Override
    public Element serialize(@NotNull CompositeArrangementEntryMatcher matcher) {
      ArrangementEntryMatcherSerializationTag tag = matcher.getOperator() == ArrangementOperator.AND
                                                    ? ArrangementEntryMatcherSerializationTag.AND
                                                    : ArrangementEntryMatcherSerializationTag.OR; 
      Element result = new Element(tag.toString());
      for (ArrangementEntryMatcher childMatcher : matcher.getMatchers()) {
        Element element = DefaultArrangementEntryMatcherSerializer.this.serialize(childMatcher);
        if (element != null) {
          result.addContent(element);
        }
      }
      return result;
    }

    @NotNull
    @Override
    public CompositeArrangementEntryMatcher deserialize(@NotNull Element element) throws IllegalArgumentException {
      ArrangementOperator operator = ArrangementEntryMatcherSerializationTag.OR.toString().equals(element.getName())
                                                    ? ArrangementOperator.OR 
                                                    : ArrangementOperator.AND;

      CompositeArrangementEntryMatcher result = null;
      for (Object o : element.getChildren()) {
        ArrangementEntryMatcher matcher = DefaultArrangementEntryMatcherSerializer.this.deserialize((Element)o);
        if (matcher == null) {
          continue;
        }
        if (result == null) {
          result = new CompositeArrangementEntryMatcher(operator, matcher);
        }
        else {
          result.addMatcher(matcher);
        }
      }
      if (result == null) {
        throw new IllegalArgumentException(String.format("Can't deserialize a matcher from element '%s'", element));
      }
      return result;
    }
  }
}
