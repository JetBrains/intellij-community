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
package org.jetbrains.jps.gant;

import org.apache.tools.ant.BuildException;
import org.codehaus.gant.ant.Gant;

import java.lang.reflect.Field;

/**
 * @author nik
 */
public class GantWithClasspathTask extends Gant {
  @Override
  public void execute() throws BuildException {
    ClassLoader oldLoader = Thread.currentThread().getContextClassLoader();
    try {
      ClassLoader loader = getClass().getClassLoader();
      Thread.currentThread().setContextClassLoader(loader);
      try {
        Field field = Gant.class.getDeclaredField("file");
        field.setAccessible(true);
        getProject().log("Starting gant script " + field.get(this));
      }
      catch (Exception ignore) {
      }
      super.execute();
    }
    finally {
      Thread.currentThread().setContextClassLoader(oldLoader);
    }
  }
}
