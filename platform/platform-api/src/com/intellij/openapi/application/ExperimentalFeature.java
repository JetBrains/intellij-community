// Copyright 2000-2017 JetBrains s.r.o.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.intellij.openapi.application;

import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;

/**
 * @author Konstantin Bulenkov
 */
public abstract class ExperimentalFeature {
  @Attribute("id")
  public String id;

  @Attribute("percentOfUsers")
  public int percentOfUsers = 0;

  @Attribute("internalFeature")
  public boolean internalFeature = false;

  @Tag("description")
  public String description;

  @Attribute("requireRestart")
  public boolean requireRestart = false;

  public abstract boolean isEnabled();
}
