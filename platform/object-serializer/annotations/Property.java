// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.serialization;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * For security reasons serializer cannot write actual class name and cannot instantiate class by name from serialized data.
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface Property {
  // Kotlin is not used because forces to use KClass - but definitely KClass should be not used
  Class[] allowedTypes() default {};
}