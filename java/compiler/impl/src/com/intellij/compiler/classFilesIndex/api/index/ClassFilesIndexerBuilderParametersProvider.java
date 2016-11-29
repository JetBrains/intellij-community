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
    final List<String> args = new ArrayList<>();
    myIndicesHolder.visitConfigures(new ConfigureVisitor() {
      @Override
      public void visit(ClassFilesIndexConfigure<?, ?> configure, boolean isAvailable) {
        final String className = configure.getIndexerBuilderClass().getCanonicalName();
        args.add(className);
        if (!isAvailable) {
          configure.prepareToIndexing(myIndicesHolder.getProject());
        }
      }
    });
    return args.size() != 0
           ? Collections.singletonList("-D" + ClassFilesIndicesBuilder.PROPERTY_NAME + "=" + StringUtil.join(args, ";"))
           : Collections.<String>emptyList();
  }
}
