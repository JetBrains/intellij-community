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
package com.intellij.rt.ant.execution;

import java.lang.reflect.InvocationTargetException;

public final class AntMain2 {

  public static void main(String[] args) throws ClassNotFoundException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
    
    try {
      IdeaAntLogger2.guardStreams();

      // as we build classpath ourselves, and ensure all libraries are added to classpath, 
      // preferred way for us to run ant will be using the traditional ant entry point, via the "Main" class 
      try {
        final Class<?> antMain = Class.forName("org.apache.tools.ant.Main");
        antMain.getMethod("main", new Class[]{args.getClass()}).invoke(null, new Object[]{args});
        return;
      }
      catch (ClassNotFoundException e) {
        // ignore
      }

      // fallback: try the newer approach, launcher
      // This approach is less preferred in our case, but still...
      // From the ant documentation: "You should start the launcher with the most minimal classpath possible, generally just the ant-launcher.jar."
      final Class<?> antLauncher = Class.forName("org.apache.tools.ant.launch.Launcher");
      antLauncher.getMethod("main", new Class[]{args.getClass()}).invoke(null, new Object[]{args});
    }
    catch (UnsupportedClassVersionError ucv) {
      String jreVersion = System.getProperty("java.version");
      String message = "\nThe version of the executed Ant is not compatible with the JRE version " + jreVersion + ". " +
                       "\nTo use Ant with this Java version, download an older version of Ant, " +
                       "unpack it and specify the path to it on the 'Execution' tab in the 'Build File Properties' dialog " +
                       "(accessible via the 'Ant Build' tool window -> Properties button).";
      throw new IllegalStateException(message) {
        @Override
        public synchronized Throwable fillInStackTrace() {
          return this; 
        }
      };
    }

  }
}
