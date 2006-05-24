/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml;

/**
 * @author peter
 */
public @interface Scope {
  Class<? extends ScopeProvider> value();
}
