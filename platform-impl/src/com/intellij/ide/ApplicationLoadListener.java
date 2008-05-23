package com.intellij.ide;

import com.intellij.openapi.application.Application;


public interface ApplicationLoadListener {
  void beforeApplicationLoaded(Application application);
}
