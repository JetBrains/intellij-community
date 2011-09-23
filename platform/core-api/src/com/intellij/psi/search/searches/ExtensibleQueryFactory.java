/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.psi.search.searches;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.extensions.SimpleSmartExtensionPoint;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.QueryExecutor;
import com.intellij.util.QueryFactory;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author yole
 */
public class ExtensibleQueryFactory<Result, Parameters> extends QueryFactory<Result, Parameters> {
  private final NotNullLazyValue<SimpleSmartExtensionPoint<QueryExecutor<Result,Parameters>>> myPoint;

  protected ExtensibleQueryFactory() {
    this("com.intellij");
  }

  protected ExtensibleQueryFactory(@NonNls final String epNamespace) {
    myPoint = new NotNullLazyValue<SimpleSmartExtensionPoint<QueryExecutor<Result, Parameters>>>() {
      @NotNull
      protected SimpleSmartExtensionPoint<QueryExecutor<Result, Parameters>> compute() {
        return new SimpleSmartExtensionPoint<QueryExecutor<Result, Parameters>>(new SmartList<QueryExecutor<Result, Parameters>>()){
          @NotNull
          protected ExtensionPoint<QueryExecutor<Result, Parameters>> getExtensionPoint() {
            @NonNls String epName = ExtensibleQueryFactory.this.getClass().getName();
            int pos = epName.lastIndexOf('.');
            if (pos >= 0) {
              epName = epName.substring(pos+1);
            }
            epName = epNamespace + "." + StringUtil.decapitalize(epName);
            return Extensions.getRootArea().getExtensionPoint(epName);
          }
        };
      }
    };
  }

  public void registerExecutor(final QueryExecutor<Result, Parameters> queryExecutor, Disposable parentDisposable) {
    registerExecutor(queryExecutor);
    Disposer.register(parentDisposable, new Disposable() {
      public void dispose() {
        unregisterExecutor(queryExecutor);
      }
    });
  }

  public void registerExecutor(@NotNull final QueryExecutor<Result, Parameters> queryExecutor) {
    myPoint.getValue().addExplicitExtension(queryExecutor);
  }

  public void unregisterExecutor(@NotNull final QueryExecutor<Result, Parameters> queryExecutor) {
    myPoint.getValue().removeExplicitExtension(queryExecutor);
  }

  @Override
  @NotNull
  protected List<QueryExecutor<Result, Parameters>> getExecutors() {
    return myPoint.getValue().getExtensions();
  }
}