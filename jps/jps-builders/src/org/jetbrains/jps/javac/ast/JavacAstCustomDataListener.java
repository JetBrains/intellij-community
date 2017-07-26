/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.jps.javac.ast;

import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.backwardRefs.javac.ast.api.JavacFileData;
import org.jetbrains.jps.incremental.java.CustomOutputDataListener;

public class JavacAstCustomDataListener implements CustomOutputDataListener {
  private final Consumer<JavacFileData> myConsumer;

  public JavacAstCustomDataListener() {
    myConsumer = InProcessRefCollectorCompilerToolExtension.createFileDataConsumer();
  }

  @NotNull
  @Override
  public String getId() {
    return ExternalRefCollectorCompilerToolExtension.ID;
  }

  @Override
  public void processData(@Nullable String dataName, @NotNull byte[] content) {
    myConsumer.consume(JavacFileData.fromBytes(content));
  }
}
