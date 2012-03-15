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
package com.intellij.core;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.indexing.FileBasedIndexInitializer;
import junit.framework.TestCase;

public class JavaCoreEnvironmentWithIndexTest extends TestCase {
  public void testStart() throws Exception {
    Disposable disposable = new MyDisposable();
    JavaCoreEnvironmentWithIndex javaCoreEnvironment = new JavaCoreEnvironmentWithIndex(disposable);
    try
    {
      Application application = ApplicationManager.getApplication();
      //FileBasedIndex fileBasedIndex = application.getComponent(FileBasedIndex.class);
      FileBasedIndexInitializer initializer = application.getComponent(FileBasedIndexInitializer.class);
    }
    finally {
      Disposer.dispose(disposable);
    }
  }

  //public void testStart() throws Exception {
  //  Disposable disposable = new MyDisposable();
  //  JavaCoreEnvironmentWithIndex javaCoreEnvironment = new JavaCoreEnvironmentWithIndex(disposable);
  //  try
  //  {
  //    Application application = ApplicationManager.getApplication();
  //    FileBasedIndex fileBasedIndex = application.getComponent(FileBasedIndex.class);
  //    fileBasedIndex.
  //  }
  //  finally {
  //    Disposer.dispose(disposable);
  //  }
  //}

  private class MyDisposable implements Disposable {
    @Override
    public void dispose() {
    }
  }
}
