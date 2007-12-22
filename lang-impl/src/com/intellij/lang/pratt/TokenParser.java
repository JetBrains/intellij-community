/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.lang.pratt;

/**
 * @author peter
 */
public interface TokenParser {
  boolean parseToken(PrattBuilder builder);
}
