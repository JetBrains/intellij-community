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
package com.intellij.diagnostic;

public class Developer {
  public static final Developer NULL = new NullDeveloper();

  private Integer myId;

  private String myName;

  private Developer() {
  }

  public Developer(int id, String name) {
    myId = id;
    myName = name;
  }

  public Integer getId() {
    return myId;
  }

  public String getDisplayText() {
    return myName;
  }

  public String getSearchableText() {
    return myName;
  }

  @Override
  public String toString() {
    return String.format("%d - %s", myId, myName);
  }

  private static class NullDeveloper extends Developer {
    @Override
    public Integer getId() {
      return null;
    }

    @Override
    public String getDisplayText() {
      return "<none>";
    }

    @Override
    public String getSearchableText() {
      return "";
    }

    @Override
    public String toString() {
      return "NullDeveloper";
    }
  }
}
