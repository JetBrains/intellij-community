/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.testIntegration;

import com.intellij.execution.Location;
import com.intellij.execution.TestStateStorage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Ref;

import java.util.Collection;

public class DeadTestsCleaner implements Runnable {
  private final TestStateStorage myTestStorage;
  private final Collection<String> myTestUrls;
  private final TestLocator myTestLocator;

  public DeadTestsCleaner(TestStateStorage storage, Collection<String> urls, TestLocator locator) {
    myTestStorage = storage;
    myTestUrls = urls;
    myTestLocator = locator;
  }

  @Override
  public void run() {
    for (String url : myTestUrls) {
      processUrl(url);
    }
  }

  private void processUrl(final String url) {
    final Ref<Location> locationRef = Ref.create();
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      @Override
      public void run() {
        Location location = myTestLocator.getLocation(url);
        locationRef.set(location);
      }
    });
    if (locationRef.get() == null) {
      myTestStorage.removeState(url);
    }
  }
}