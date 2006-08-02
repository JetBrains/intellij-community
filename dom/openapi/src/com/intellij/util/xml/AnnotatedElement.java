/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml;

import org.jetbrains.annotations.Nullable;

import java.lang.annotation.Annotation;

/**
 * @author peter
 */
public interface AnnotatedElement {

  @Nullable
    <T extends Annotation> T getAnnotation(Class<T> annotationClass);
}
