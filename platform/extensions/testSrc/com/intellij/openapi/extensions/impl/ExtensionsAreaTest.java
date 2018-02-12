/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.extensions.impl;

import com.intellij.openapi.extensions.Extensions;
import org.junit.Test;
import org.picocontainer.MutablePicoContainer;
import org.picocontainer.defaults.DefaultPicoContainer;

import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * @author mike
 */
public class ExtensionsAreaTest {
  @Test
  public void testGetComponentAdapterDoesntDuplicateAdapters() {
    MutablePicoContainer picoContainer =
      new ExtensionsAreaImpl("foo", null, new DefaultPicoContainer(), new Extensions.SimpleLogProvider()).getPicoContainer();
    picoContainer.registerComponentImplementation("runnable", ExtensionsAreaTest.class);

    List adapters = picoContainer.getComponentAdaptersOfType(ExtensionsAreaTest.class);
    assertEquals(1, adapters.size());
  }
}
