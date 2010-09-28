/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.openapi.ui;

import com.intellij.ide.ui.SplitterProportionsDataImpl;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.Tag;

/**
 * @author nik
 */
public class MasterDetailsState {
  private SplitterProportionsDataImpl proportions = new SplitterProportionsDataImpl();
  private String lastEditedConfigurable;

  @Property(surroundWithTag = false)
  public SplitterProportionsDataImpl getProportions() {
    return proportions;
  }

  public void setProportions(SplitterProportionsDataImpl proportions) {
    this.proportions = proportions;
  }

  @Tag("last-edited")
  public String getLastEditedConfigurable() {
    return lastEditedConfigurable;
  }

  public void setLastEditedConfigurable(String lastEditedConfigurable) {
    this.lastEditedConfigurable = lastEditedConfigurable;
  }
}
