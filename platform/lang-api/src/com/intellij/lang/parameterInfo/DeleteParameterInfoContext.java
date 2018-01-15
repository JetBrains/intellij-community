/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.lang.parameterInfo;

import com.intellij.openapi.util.UserDataHolderEx;

public interface DeleteParameterInfoContext {
  UserDataHolderEx getCustomContext();
}
