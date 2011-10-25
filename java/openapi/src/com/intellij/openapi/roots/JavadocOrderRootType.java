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
package com.intellij.openapi.roots;

/**
 * @author yole
 */
public class JavadocOrderRootType extends PersistentOrderRootType {
  private JavadocOrderRootType() {
    super("JAVADOC", "javadocPath", "javadoc-paths", "javadocPathEntry");
  }

  /**
   * JavaDoc paths.
   */
  public static OrderRootType getInstance() {
    return getOrderRootType(JavadocOrderRootType.class);
  }

  @Override
  public boolean collectFromDependentModules() {
    return true;
  }
}
