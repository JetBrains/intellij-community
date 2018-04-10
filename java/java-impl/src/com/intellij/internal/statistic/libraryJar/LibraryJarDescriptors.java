/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.internal.statistic.libraryJar;

import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.XCollection;

public class LibraryJarDescriptors {
  @Property(surroundWithTag = false)
  @XCollection
  public LibraryJarDescriptor[] myDescriptors;

  public LibraryJarDescriptor[] getDescriptors() {
    return myDescriptors;
  }
}
