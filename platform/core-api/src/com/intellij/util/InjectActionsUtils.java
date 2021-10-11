// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.openapi.util.Key;

public class InjectActionsUtils {
  public static final Key<Boolean> ENABLED_FOR_HOST = Key.create("inject language action enabled for host");
}
