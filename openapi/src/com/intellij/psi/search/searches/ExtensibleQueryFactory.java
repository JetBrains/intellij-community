/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
 *
 */

package com.intellij.psi.search.searches;

import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.QueryExecutor;
import com.intellij.util.QueryFactory;
import org.jetbrains.annotations.NonNls;

import java.util.List;

/**
 * @author yole
 */
public class ExtensibleQueryFactory<Result, Parameters> extends QueryFactory<Result, Parameters> {
  private boolean myExtensionsLoaded = false;

  @Override
  protected List<QueryExecutor<Result, Parameters>> getExecutors() {
    synchronized(myExecutors) {
      if (!myExtensionsLoaded) {
        myExtensionsLoaded = true;
        @NonNls String epName = getClass().getName();
        int pos = epName.lastIndexOf('.');
        if (pos >= 0) {
          epName = epName.substring(pos+1);
        }
        epName = "com.intellij." + StringUtil.decapitalize(epName);

        for(Object ext: Extensions.getExtensions(epName)) {
          //noinspection unchecked
          myExecutors.add((QueryExecutor<Result,Parameters>) ext);
        }
      }
      return myExecutors;
    }
  }
}