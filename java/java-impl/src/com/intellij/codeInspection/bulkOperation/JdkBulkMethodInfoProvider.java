// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.bulkOperation;

import com.intellij.psi.CommonClassNames;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.stream.Stream;

public final class JdkBulkMethodInfoProvider implements BulkMethodInfoProvider {
  private static final BulkMethodInfo[] INFOS = {
    new BulkMethodInfo(CommonClassNames.JAVA_UTIL_COLLECTION, "add", "addAll", CommonClassNames.JAVA_UTIL_COLLECTION),
    new BulkMethodInfo(CommonClassNames.JAVA_UTIL_MAP, "put", "putAll", CommonClassNames.JAVA_UTIL_MAP),
  };

  @Override
  public @NotNull Stream<BulkMethodInfo> consumers() {
    return Arrays.stream(INFOS);
  }
}
