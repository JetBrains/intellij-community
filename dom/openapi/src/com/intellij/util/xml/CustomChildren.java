/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;

/**
 * Annotates a collection children getter, which should return parameterized {@link java.util.Collection} or {@link java.util.List}. The returned elements
 * are those who don't belong to any of the usual collection children (see {@link com.intellij.util.xml.SubTagList}).
 *
 * @author peter
 */

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface CustomChildren {
}