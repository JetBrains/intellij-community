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
import com.intellij.execution.testframework.JavaTestLocator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.search.GlobalSearchScope;

import java.util.List;

public class TestLocator {
  private final Project myProject;

  public TestLocator(Project project) {
    myProject = project;
  }

  public Location getLocation(String url) {
    return getLocation(url, myProject);
  }
  
  public static Location getLocation(String url, Project project) {
    String protocol = VirtualFileManager.extractProtocol(url);
    String path = VirtualFileManager.extractPath(url);

    if (protocol != null) {
      List<Location> locations = JavaTestLocator.INSTANCE.getLocation(protocol, path, project, GlobalSearchScope.allScope(project));
      if (!locations.isEmpty()) {
        return locations.get(0);
      }
    }

    return null;
  }
  
  public static boolean canLocate(String url) {
    return isSuite(url) || isTest(url);
  }
  
  public static boolean isSuite(String url) {
    String protocol = VirtualFileManager.extractProtocol(url);
    return JavaTestLocator.SUITE_PROTOCOL.equals(protocol);
  }

  public static boolean isTest(String url) {
    String protocol = VirtualFileManager.extractProtocol(url);
    return JavaTestLocator.TEST_PROTOCOL.equals(protocol);
  }
  
}