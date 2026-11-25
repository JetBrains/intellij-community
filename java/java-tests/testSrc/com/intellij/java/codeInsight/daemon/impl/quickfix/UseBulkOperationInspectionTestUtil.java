// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInspection.bulkOperation.BulkMethodInfo;
import com.intellij.codeInspection.bulkOperation.BulkMethodInfoProvider;
import com.intellij.codeInspection.bulkOperation.UseBulkOperationInspection;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.CommonClassNames;
import com.intellij.testFramework.ServiceContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.stream.Stream;

/**
 * The shared setup of the quick fix tests of {@link UseBulkOperationInspection}.
 */
final class UseBulkOperationInspectionTestUtil {
  private static final BulkMethodInfoProvider TEST_PROVIDER = new BulkMethodInfoProvider() {
    @NotNull
    @Override
    public Stream<BulkMethodInfo> consumers() {
      return Stream.of(new BulkMethodInfo("testpackage.TestClass", "test", "test", CommonClassNames.JAVA_LANG_ITERABLE));
    }
  };

  private UseBulkOperationInspectionTestUtil() {
  }

  /**
   * Returns the inspection with {@code USE_ARRAYS_AS_LIST} on.
   */
  static @NotNull UseBulkOperationInspection createInspection() {
    UseBulkOperationInspection inspection = new UseBulkOperationInspection();
    inspection.USE_ARRAYS_AS_LIST = true;
    return inspection;
  }

  /**
   * Registers a {@link BulkMethodInfoProvider} with the bulk method {@code testpackage.TestClass.test}.
   * The provider stays until {@code disposable} is disposed.
   */
  static void registerTestProvider(@NotNull Disposable disposable) {
    ServiceContainerUtil.registerExtension(ApplicationManager.getApplication(), BulkMethodInfoProvider.KEY, TEST_PROVIDER, disposable);
  }
}
