// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;

import java.util.ArrayList;
import java.util.List;

@Service(Service.Level.PROJECT)
public final class ConcurrencyAnnotationsManager {
  private static final String[] FRAMEWORKS = {
    "net.jcip.annotations",
    "javax.annotation.concurrent",
    "org.apache.http.annotation",
    "com.android.annotations.concurrency",
  };

  private static final String[] ANDROID_FRAMEWORKS = {
    "androidx.annotation",
    "android.support.annotation"
  };

  private static final String IMMUTABLE = "Immutable";
  private static final String GUARDED_BY = "GuardedBy";
  private static final String THREAD_SAFE = "ThreadSafe";
  private static final String NOT_THREAD_SAFE = "NotThreadSafe";

  private final List<String> myImmutableList = new ArrayList<>();
  private final List<String> myGuardedByList = new ArrayList<>();
  private final List<String> myThreadSafeList = new ArrayList<>();
  private final List<String> myNotThreadSafeList = new ArrayList<>();

  public ConcurrencyAnnotationsManager() {
    fillDefaults(myImmutableList, IMMUTABLE);
    fillDefaults(myGuardedByList, GUARDED_BY);
    fillDefaults(myThreadSafeList, THREAD_SAFE);
    fillDefaults(myNotThreadSafeList, NOT_THREAD_SAFE);

    for (String framework: ANDROID_FRAMEWORKS) {
      myGuardedByList.add(framework + ".GuardedBy");
      myThreadSafeList.add(framework + ".AnyThread");
    }

    myImmutableList.add("com.google.auto.value.AutoValue");
    myImmutableList.add("com.google.errorprone.annotations.Immutable");
    myGuardedByList.add("com.google.errorprone.annotations.concurrent.GuardedBy");
  }

  private static void fillDefaults(List<? super String> list, final String annoName) {
    list.addAll(ContainerUtil.map(FRAMEWORKS, framework -> framework + "." + annoName));
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
