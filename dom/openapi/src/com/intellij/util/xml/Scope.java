/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * @author peter
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface Scope {
  Class<? extends ScopeProvider> value();
}
