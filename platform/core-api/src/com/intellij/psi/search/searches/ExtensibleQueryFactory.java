// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.search.searches;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.QueryExecutor;
import com.intellij.util.QueryFactory;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

import java.beans.Introspector;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Query factories are static, but one JVM may host several applications (tests),
 * and the language server root area depends on the current analyzer context.
 * So the extension point is resolved on each call and never cached.
 */
public class ExtensibleQueryFactory<Result, Parameters> extends QueryFactory<Result, Parameters> {
  private final @NotNull Supplier<? extends ExtensionPoint<QueryExecutor<Result, Parameters>>> extensionPoint;

  protected ExtensibleQueryFactory() {
    this("com.intellij");
  }

  protected ExtensibleQueryFactory(@NotNull ExtensionPointName<QueryExecutor<Result, Parameters>> epName) {
    extensionPoint = () -> ApplicationManager.getApplication().getExtensionArea().getExtensionPoint(epName);
  }

  /**
   * @deprecated Please specify the extension point name explicitly
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  protected ExtensibleQueryFactory(@NonNls String epNamespace) {
    // the supplier runs on each query, so compute the name once
    @NonNls String className = this.getClass().getName();
    int pos = className.lastIndexOf('.');
    if (pos >= 0) {
      className = className.substring(pos+1);
    }
    @NonNls String epName = epNamespace + "." + Introspector.decapitalize(className);
    extensionPoint = () -> ApplicationManager.getApplication().getExtensionArea().getExtensionPoint(epName);
  }

  @VisibleForTesting
  ExtensibleQueryFactory(@NotNull Supplier<? extends ExtensionPoint<QueryExecutor<Result, Parameters>>> extensionPoint) {
    this.extensionPoint = extensionPoint;
  }

  public void registerExecutor(QueryExecutor<Result, Parameters> queryExecutor, Disposable parentDisposable) {
    registerExecutor(queryExecutor);
    Disposer.register(parentDisposable, new Disposable() {
      @Override
      public void dispose() {
        unregisterExecutor(queryExecutor);
      }
    });
  }

  @Override
  protected @NotNull List<QueryExecutor<Result, Parameters>> getExecutors() {
    // the EP caches its immutable extension list
    List<QueryExecutor<Result, Parameters>> extensions = extensionPoint.get().getExtensionList();
    List<QueryExecutor<Result, Parameters>> explicitExecutors = super.getExecutors();
    if (explicitExecutors.isEmpty()) {
      return extensions;
    }
    List<QueryExecutor<Result, Parameters>> result = new ArrayList<>(explicitExecutors.size() + extensions.size());
    result.addAll(explicitExecutors);
    result.addAll(extensions);
    return result;
  }
}