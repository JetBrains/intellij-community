/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.compiler.classFilesIndex.api.index;

import com.intellij.compiler.server.BuildProcessParametersProvider;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.classFilesIndex.indexer.api.ClassFilesIndicesBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Dmitry Batkovich
 */
public class ClassFilesIndexerBuilderParametersProvider extends BuildProcessParametersProvider {
  private final ClassFilesIndexFeaturesHolder myIndicesHolder;

  protected ClassFilesIndexerBuilderParametersProvider(final ClassFilesIndexFeaturesHolder indicesHolder) {
    myIndicesHolder = indicesHolder;
  }

  @NotNull
  @Override
  public List<String> getVMArguments() {
    final List<String> args = new ArrayList<String>();
    myIndicesHolder.visitEnabledConfigures(
      new Processor<ClassFilesIndexConfigure>() {
      @Override
      public boolean process(final ClassFilesIndexConfigure availableConfigure) {
        final String className = availableConfigure.getIndexerBuilderClass().getCanonicalName();
        args.add(className);
        return true;
      }
    }, new Processor<ClassFilesIndexConfigure>() {
        @Override
        public boolean process(final ClassFilesIndexConfigure notAvailableConfigure) {
          final String className = notAvailableConfigure.getIndexerBuilderClass().getCanonicalName();
          args.add(className);
          notAvailableConfigure.prepareToIndexing(myIndicesHolder.getProject());
          return true;
        }
      }
    );
    if (args.size() != 0) {
      final String serializedArgs = StringUtil.join(args, ";");
      return Collections.singletonList("-D" + ClassFilesIndicesBuilder.PROPERTY_NAME + "=" + serializedArgs);
    }
    else {
      return Collections.emptyList();
    }
  }

}
