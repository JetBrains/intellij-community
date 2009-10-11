/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.lang.pratt;

import com.intellij.openapi.util.Trinity;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.containers.MultiMap;

import java.util.Collection;
import java.util.Collections;

/**
 * @author peter
 */
public class PrattRegistry {
  private static final MultiMap<IElementType, Trinity<Integer, PathPattern, TokenParser>> ourMap = new MultiMap<IElementType, Trinity<Integer, PathPattern, TokenParser>>();

  public static void registerParser(IElementType type, int priority, TokenParser parser) {
    registerParser(type, priority, PathPattern.path(), parser);
  }

  public static void registerParser(final IElementType type, final int priority, final PathPattern pattern, final TokenParser parser) {
    ourMap.putValue(type, new Trinity<Integer, PathPattern, TokenParser>(priority, pattern, parser));
  }

  public static Collection<Trinity<Integer, PathPattern, TokenParser>> getParsers(IElementType type) {
    final Collection<Trinity<Integer,PathPattern,TokenParser>> trinities = ourMap.get(type);
    return trinities == null ? Collections.<Trinity<Integer, PathPattern, TokenParser>>emptyList() : trinities;
  }

}
