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

import com.intellij.execution.testframework.sm.runner.states.TestStateInfo;
import com.intellij.openapi.util.Condition;
import com.intellij.util.containers.ContainerUtil;

import java.util.Collection;
import java.util.Date;
import java.util.List;

public class TestInfo {
  private final Date runDate;
  private final String url;
  private final TestStateInfo.Magnitude magnitude;

  public TestInfo(String url, TestStateInfo.Magnitude magnitude, Date runDate) {
    this.url = url;
    this.magnitude = magnitude;
    this.runDate = runDate;
  }
  
  public Date getRunDate() {
    return runDate;
  }

  public String getUrl() {
    return url;
  }

  public TestStateInfo.Magnitude getMagnitude() {
    return magnitude;
  }

  public static <T extends TestInfo> List<T> select(Collection<T> infos, final TestStateInfo.Magnitude... magnitudes) {
    return ContainerUtil.filter(infos, new Condition<T>() {
      @Override
      public boolean value(T t) {
        for (TestStateInfo.Magnitude magnitude : magnitudes) {
          if (t.getMagnitude() == magnitude) {
            return true;
          }
        }
        return false;
      }
    });
  }
}