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
package com.intellij.openapi.application;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Getter;
import com.intellij.openapi.vfs.encoding.EncodingRegistry;
import org.jetbrains.annotations.NotNull;

/**
 * Provides access to the <code>Application</code>.
 */
public class ApplicationManager {
  protected static Application ourApplication = null;

  /**
   * Gets Application.
   *
   * @return <code>Application</code>
   */
  public static Application getApplication(){
    return ourApplication;
  }

  private static void setApplication(Application instance) {
    ourApplication = instance;
    CachedSingletonsRegistry.cleanupCachedFields();
  }

  public static void setApplication(Application instance, @NotNull Disposable parent) {
    final Application old = ourApplication;
    Disposer.register(parent, new Disposable() {
      @Override
      public void dispose() {
        if (old != null) { // to prevent NPEs in threads still running
          setApplication(old);
        }
      }
    });
    setApplication(instance);
  }

  public static void setApplication(Application instance, 
                                    Getter<FileTypeRegistry> fileTypeRegistryGetter,
                                    Getter<EncodingRegistry> encodingRegistryGetter,
                                    @NotNull Disposable parent) {
    final Application old = ourApplication;
    final Getter<FileTypeRegistry> oldFileTypeRegistry = FileTypeRegistry.ourInstanceGetter;
    final Getter<EncodingRegistry> oldEncodingRegistry = EncodingRegistry.ourInstanceGetter;
    Disposer.register(parent, new Disposable() {
      @Override
      public void dispose() {
        if (old != null) { // to prevent NPEs in threads still running
          setApplication(old);
          //noinspection AssignmentToStaticFieldFromInstanceMethod
          FileTypeRegistry.ourInstanceGetter = oldFileTypeRegistry;
          EncodingRegistry.ourInstanceGetter = oldEncodingRegistry;
        }
      }
    });
    setApplication(instance);
    FileTypeRegistry.ourInstanceGetter = fileTypeRegistryGetter;
    EncodingRegistry.ourInstanceGetter = encodingRegistryGetter;
  }
}
