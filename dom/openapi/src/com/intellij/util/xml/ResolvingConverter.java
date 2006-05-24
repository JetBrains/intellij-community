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
package com.intellij.util.xml;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Arrays;
import java.util.Collections;

/**
 * @author peter
 */
public interface ResolvingConverter<T> extends Converter<T> {
  ResolvingConverter EMPTY_CONVERTER = new ResolvingConverter() {
    public Collection getVariants(final ConvertContext context) {
      return Collections.emptyList();
    }

    public Object fromString(final String s, final ConvertContext context) {
      return s;
    }

    public String toString(final Object t, final ConvertContext context) {
      return String.valueOf(t);
    }
  };

  Converter<Boolean> BOOLEAN_CONVERTER = new ResolvingConverter<Boolean>() {
    public Boolean fromString(final String s, final ConvertContext context) {
      if ("true".equalsIgnoreCase(s)) {
        return Boolean.TRUE;
      }
      if ("false".equalsIgnoreCase(s)) {
        return Boolean.FALSE;
      }
      return null;
    }

    public String toString(final Boolean t, final ConvertContext context) {
      return t == null? null:t.toString();
    }

    @NotNull
    public Collection<Boolean> getVariants(final ConvertContext context) {
      return Arrays.asList(Boolean.FALSE, Boolean.TRUE);
    }
  };

  @NotNull
  Collection<T> getVariants(final ConvertContext context);
}
