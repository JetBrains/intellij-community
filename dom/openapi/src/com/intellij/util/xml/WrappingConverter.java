/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

package com.intellij.util.xml;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Gregory.Shrago
 */
public abstract class WrappingConverter extends Converter<Object> {
  public Object fromString(@Nullable @NonNls String s, final ConvertContext context) {
    final Converter converter = getConverter((GenericDomValue)context.getInvocationElement());
    if (converter != null) {
      return converter.fromString(s, context);
    }
    else {
      return s;
    }
  }

  public String toString(@Nullable Object t, final ConvertContext context) {
    final Converter converter = getConverter((GenericDomValue)context.getInvocationElement());
    if (converter != null) {
      return converter.toString(t, context);
    }
    else {
      return String.valueOf(t);
    }
  }

  @Nullable
  public abstract Converter<?> getConverter(@NotNull final GenericDomValue domElement);

  public static Converter getDeepestConverter(final Converter converter, final GenericDomValue domValue) {
    Converter cur = converter;
    for (Converter next; cur instanceof WrappingConverter; cur = next) {
      next = ((WrappingConverter)cur).getConverter(domValue);
      if (next == null) break;
    }
    return cur;
  }
}
