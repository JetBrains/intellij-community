// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.search.searches;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.QueryExecutor;
import com.intellij.util.QueryFactory;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.beans.Introspector;
import java.util.List;

public class ExtensibleQueryFactory<Result, Parameters> extends QueryFactory<Result, Parameters> {
  private final SmartExtensionPoint<QueryExecutor<Result, Parameters>> point;

  protected ExtensibleQueryFactory() {
    this("com.intellij");
  }

  protected ExtensibleQueryFactory(@NotNull ExtensionPointName<QueryExecutor<Result, Parameters>> epName) {
    point = new SmartExtensionPoint<>(() -> ApplicationManager.getApplication().getExtensionArea().getExtensionPoint(epName));
  }

  /**
   * @deprecated Please specify the extension point name explicitly
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  protected ExtensibleQueryFactory(@NonNls String epNamespace) {
    point = new SmartExtensionPoint<>(() -> {
      @NonNls String epName = this.getClass().getName();
      int pos = epName.lastIndexOf('.');
      if (pos >= 0) {
        epName = epName.substring(pos+1);
      }
      epName = epNamespace + "." + Introspector.decapitalize(epName);
      return ApplicationManager.getApplication().getExtensionArea().getExtensionPoint(epName);
    });
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
  public void registerExecutor(@NotNull QueryExecutor<Result, Parameters> queryExecutor) {
    point.addExplicitExtension(queryExecutor);
  }

  @Override
  public void unregisterExecutor(@NotNull QueryExecutor<Result, Parameters> queryExecutor) {
    point.removeExplicitExtension(queryExecutor);
  }

  @Override
  protected @NotNull List<QueryExecutor<Result, Parameters>> getExecutors() {
    return point.getExtensions();
  }
}