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
package com.intellij.debugger.settings;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 * Date: Apr 12, 2005
 */
public class CompositeDataBinding implements DataBinding{
  private final List<DataBinding> myBindings = new ArrayList<>();

  void addBinding(DataBinding binding) {
    myBindings.add(binding);
  }

  public void loadData(Object from) {
    for (DataBinding myBinding : myBindings) {
      myBinding.loadData(from);
    }
  }

  public void saveData(Object to) {
    for (DataBinding myBinding : myBindings) {
      myBinding.saveData(to);
    }
  }

  public boolean isModified(Object obj) {
    for (DataBinding myBinding : myBindings) {
      if (myBinding.isModified(obj)) {
        return true;
      }
    }
    return false;
  }
}
