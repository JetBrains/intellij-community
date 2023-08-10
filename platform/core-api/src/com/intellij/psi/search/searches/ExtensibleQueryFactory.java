// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.search.searches;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.extensions.*;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.QueryExecutor;
import com.intellij.util.QueryFactory;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.beans.Introspector;
import java.util.List;

public class ExtensibleQueryFactory<Result, Parameters> extends QueryFactory<Result, Parameters> {
  private final SmartExtensionPoint<QueryExecutor<Result, Parameters>, QueryExecutor<Result, Parameters>> myPoint;

  protected ExtensibleQueryFactory() {
    this("com.intellij");
  }

  protected ExtensibleQueryFactory(@NotNull ExtensionPointName<QueryExecutor<Result, Parameters>> epName) {
    myPoint = new SimpleSmartExtensionPoint<QueryExecutor<Result, Parameters>>() {
      @Override
      protected @NotNull ExtensionPoint<QueryExecutor<Result, Parameters>> getExtensionPoint() {
        return Extensions.getRootArea().getExtensionPoint(epName);
      }
    };
  }

  /**
   * @deprecated Please specify the extension point name explicitly
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  protected ExtensibleQueryFactory(final @NonNls String epNamespace) {
    myPoint = new SimpleSmartExtensionPoint<QueryExecutor<Result, Parameters>>() {
      @Override
      protected @NotNull ExtensionPoint<QueryExecutor<Result, Parameters>> getExtensionPoint() {
        @NonNls String epName = ExtensibleQueryFactory.this.getClass().getName();
        int pos = epName.lastIndexOf('.');
        if (pos >= 0) {
          epName = epName.substring(pos+1);
        }
        epName = epNamespace + "." + Introspector.decapitalize(epName);
        return Extensions.getRootArea().getExtensionPoint(epName);
      }
    };
  }

  public void registerExecutor(final QueryExecutor<Result, Parameters> queryExecutor, Disposable parentDisposable) {
    registerExecutor(queryExecutor);
    Disposer.register(parentDisposable, new Disposable() {
      @Override
      public void dispose() {
        unregisterExecutor(queryExecutor);
      }
    });
  }

  @Override
  public void registerExecutor(final @NotNull QueryExecutor<Result, Parameters> queryExecutor) {
    myPoint.addExplicitExtension(queryExecutor);
  }

  @Override
  public void unregisterExecutor(final @NotNull QueryExecutor<Result, Parameters> queryExecutor) {
    myPoint.removeExplicitExtension(queryExecutor);
  }

  @Override
  protected @NotNull List<QueryExecutor<Result, Parameters>> getExecutors() {
    return myPoint.getExtensions();
  }
}