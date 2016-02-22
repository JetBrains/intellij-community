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
package com.intellij.codeInsight;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;

import java.util.ArrayList;
import java.util.List;

public class ConcurrencyAnnotationsManager {
  private static final String[] FRAMEWORKS = {"net.jcip.annotations", "javax.annotation.concurrent", "org.apache.http.annotation"};
  
  private static final String IMMUTABLE = "Immutable";
  private static final String GUARDED_BY = "GuardedBy";
  private static final String THREAD_SAFE = "ThreadSafe";
  private static final String NOT_THREAD_SAFE = "NotThreadSafe";

  private List<String> myImmutableList = new ArrayList<String>();
  private List<String> myGuardedByList = new ArrayList<String>();
  private List<String> myThreadSafeList = new ArrayList<String>();
  private List<String> myNotThreadSafeList = new ArrayList<String>();

  public ConcurrencyAnnotationsManager() {
    fillDefaults(myImmutableList, IMMUTABLE);
    fillDefaults(myGuardedByList, GUARDED_BY);
    fillDefaults(myThreadSafeList, THREAD_SAFE);
    fillDefaults(myNotThreadSafeList, NOT_THREAD_SAFE);
  }

  private static void fillDefaults(List<String> list, final String annoName) {
    list.addAll(ContainerUtil.map(FRAMEWORKS, new Function<String, String>() {
      @Override
      public String fun(String framework) {
        return framework + "." + annoName;
      }
    }));
  }

  public static ConcurrencyAnnotationsManager getInstance(Project project) {
    return ServiceManager.getService(project, ConcurrencyAnnotationsManager.class);
  }

  public List<String> getImmutableAnnotations() {
    return myImmutableList;
  }
  
  public List<String> getGuardedByAnnotations() {
    return myGuardedByList;
  }

  public List<String> getThreadSafeList() {
    return myThreadSafeList;
  }

  public List<String> getNotThreadSafeList() {
    return myNotThreadSafeList;
  }
}
