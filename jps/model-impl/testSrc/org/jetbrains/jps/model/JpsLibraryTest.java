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
package org.jetbrains.jps.model;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.jps.model.java.JpsJavaLibraryType;
import org.jetbrains.jps.model.java.JpsJavaSdkType;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsLibraryCollection;
import org.jetbrains.jps.model.library.JpsOrderRootType;

public class JpsLibraryTest extends JpsModelTestCase {
  public void testAddLibrary() {
    JpsLibrary a = myProject.addLibrary("a", JpsJavaLibraryType.INSTANCE);
    JpsLibraryCollection collection = myProject.getLibraryCollection();
    assertSameElements(collection.getLibraries(), a);
    assertSameElements(ContainerUtil.newArrayList(collection.getLibraries(JpsJavaLibraryType.INSTANCE)), a);
    assertEmpty(ContainerUtil.newArrayList(collection.getLibraries(JpsJavaSdkType.INSTANCE)));
  }

  public void testAddRoot() {
    final JpsLibrary library = myProject.addLibrary("a", JpsJavaLibraryType.INSTANCE);
    library.addRoot("file://my-url", JpsOrderRootType.COMPILED);
    assertEquals("file://my-url", assertOneElement(library.getRoots(JpsOrderRootType.COMPILED)).getUrl());
  }
}
