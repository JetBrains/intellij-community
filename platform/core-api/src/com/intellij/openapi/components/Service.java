// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.components;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * @see com.intellij.openapi.application.PreloadingActivity
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface Service {
}
