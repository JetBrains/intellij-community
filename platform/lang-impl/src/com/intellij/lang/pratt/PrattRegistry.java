/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
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
