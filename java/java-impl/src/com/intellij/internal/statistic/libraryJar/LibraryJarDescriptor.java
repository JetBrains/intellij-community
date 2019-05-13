/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.internal.statistic.libraryJar;

import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;

/**
 * @author Ivan Chirkov
 */
@Tag("technology")
public class LibraryJarDescriptor {

  public static final LibraryJarDescriptor[] EMPTY = new LibraryJarDescriptor[0];

  /**
   * Name of library/framework
   */
  @Attribute("name")
  public String myName;

  /**
   * Class used to identify library/framework
   */
  @Attribute("class")
  public String myClass;
}
