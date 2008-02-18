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
package com.intellij.openapi.roots;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Root types that can be queried from OrderEntry.
 * @see OrderEntry
 * @author dsl
 */
public class OrderRootType {
  private String myName;
  private String mySdkRootName;
  private String myModulePathsName;
  private static OrderRootType[] ourPersistentOrderRootTypes = new OrderRootType[0];
  private static boolean ourExtensionsLoaded = false;

  public static final ExtensionPointName<OrderRootType> EP_NAME = ExtensionPointName.create("com.intellij.orderRootType");

  protected OrderRootType(@NonNls String name, @NonNls String sdkRootName, @NonNls String modulePathsName, boolean persistent) {
    myName = name;
    mySdkRootName = sdkRootName;
    myModulePathsName = modulePathsName;
    if (persistent) {
      //noinspection AssignmentToStaticFieldFromInstanceMethod
      ourPersistentOrderRootTypes = ArrayUtil.append(ourPersistentOrderRootTypes, this);
    }
  }

  /**
   * Classpath.
   */
  public static final OrderRootType CLASSES_AND_OUTPUT = new OrderRootType("CLASSES_AND_OUTPUT", null, null, false);

  /**
   * Classpath for compilation
   */
  public static final OrderRootType COMPILATION_CLASSES = new OrderRootType("COMPILATION_CLASSES", null, null, false);

  /**
   * Classpath without output directories for this module.
   */
  public static final OrderRootType CLASSES = new OrderRootType("CLASSES", "classPath", null, true);

  /**
   * Sources.
   */
  public static final OrderRootType SOURCES = new OrderRootType("SOURCES", "sourcePath", null, true);

  public String name() {
    return myName;
  }

  /**
   * Element name used for storing roots of this type in JDK and library definitions.
   */
  public String getSdkRootName() {
    return mySdkRootName;
  }

  /**
   * Element name used for storing roots of this type in module definitions.
   */
  public String getModulePathsName() {
    return myModulePathsName;
  }

  /**
   * Whether this root type should be skipped when writing a Library if the root type doesn't contain
   * any roots.
   *
   * @return true if empty root type should be skipped, false otherwise.
   */
  public boolean skipWriteIfEmpty() {
    return false;
  }

  public static synchronized OrderRootType[] getAllTypes() {
    if (!ourExtensionsLoaded) {
      ourExtensionsLoaded = true;
      Extensions.getExtensions(EP_NAME);
    }
    return ourPersistentOrderRootTypes;
  }

  public static List<OrderRootType> getSortedRootTypes() {
    List<OrderRootType> allTypes = new ArrayList<OrderRootType>();
    Collections.addAll(allTypes, getAllTypes());
    Collections.sort(allTypes, new Comparator<OrderRootType>() {
      public int compare(final OrderRootType o1, final OrderRootType o2) {
        return o1.getSdkRootName().compareTo(o2.getSdkRootName());
      }
    });
    return allTypes;
  }

  protected static <T> T getOrderRootType(final Class<? extends T> orderRootTypeClass) {
    for(OrderRootType rootType: Extensions.getExtensions(EP_NAME)) {
      if (orderRootTypeClass.isInstance(rootType)) {
        //noinspection unchecked
        return (T)rootType;
      }
    }
    assert false;
    return null;
  }
}
