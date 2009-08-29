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
 */
package com.intellij.openapi.deployment;

import com.intellij.openapi.compiler.CompilerBundle;
import gnu.trove.THashMap;

import java.util.Map;

public class PackagingMethod {
  public static final PackagingMethod[] EMPTY_ARRAY = new PackagingMethod[0];
  private final String myId;
  private final String myDescription;

  private static final Map<String, PackagingMethod> ourRegisteredMethods = new THashMap<String, PackagingMethod>();

  private PackagingMethod(String id, String description) {
    myId = id;
    myDescription = description;

    ourRegisteredMethods.put(id, this);
  }

  public String getId() {
    return myId;
  }

  public static final PackagingMethod DO_NOT_PACKAGE = new PackagingMethod("0", CompilerBundle.message("packaging.method.name.do.not.package"));
  public static final PackagingMethod COPY_FILES = new PackagingMethod("1",CompilerBundle.message("packaging.method.name.copy.files"));
  public static final PackagingMethod COPY_FILES_AND_LINK_VIA_MANIFEST = new PackagingMethod("2",CompilerBundle.message("packaging.method.name.copy.files.and.link.via.manifest"));
  /**
   * @deprecated
   */
  public static final PackagingMethod COPY_CLASSES = new PackagingMethod("3",CompilerBundle.message("packaging.method.name.copy.classes"));
  public static final PackagingMethod INCLUDE_MODULE_IN_BUILD = new PackagingMethod("4",CompilerBundle.message("packaging.method.name.include.module.in.build"));
  public static final PackagingMethod JAR_AND_COPY_FILE = new PackagingMethod("5",CompilerBundle.message("packaging.method.name.jar.and.copy.file"));
  public static final PackagingMethod JAR_AND_COPY_FILE_AND_LINK_VIA_MANIFEST = new PackagingMethod("6",CompilerBundle.message("packaging.method.name.jar.copy.and.link.via.manifest"));

  public static PackagingMethod getDeploymentMethodById(String id) {
    return ourRegisteredMethods.get(id);
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof PackagingMethod)) return false;

    final PackagingMethod method = (PackagingMethod)o;

    if (!myId.equals(method.myId)) return false;

    return true;
  }

  public int hashCode() {
    return myId.hashCode();
  }

  public String toString() {
    return CompilerBundle.message("packaging.method.presentation.with.description", myDescription);
  }
}
