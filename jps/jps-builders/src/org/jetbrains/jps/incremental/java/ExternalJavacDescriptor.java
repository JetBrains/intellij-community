/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.jps.incremental.java;

import com.intellij.execution.process.BaseOSProcessHandler;
import org.jetbrains.jps.incremental.GlobalContextKey;
import org.jetbrains.jps.javac.JavacServerClient;

/**
 * @author Eugene Zhuravlev
 *         Date: 1/24/12
 */
public class ExternalJavacDescriptor {
  public static final GlobalContextKey<ExternalJavacDescriptor> KEY = GlobalContextKey.create("_external_javac_descriptor_");

  public final BaseOSProcessHandler process;
  public final JavacServerClient client;

  public ExternalJavacDescriptor(BaseOSProcessHandler process, JavacServerClient client) {
    this.process = process;
    this.client = client;
  }
}
