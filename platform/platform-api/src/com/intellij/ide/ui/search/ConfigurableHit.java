/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.ide.ui.search;

import com.intellij.openapi.options.Configurable;

import java.util.Set;
import java.util.LinkedHashSet;

public final class ConfigurableHit {

  private final Set<Configurable> myNameHits = new LinkedHashSet<>();
  private final Set<Configurable> myContentHits = new LinkedHashSet<>();

  private final Set<Configurable> myNameFullHit = new LinkedHashSet<>();

  ConfigurableHit() {
  }

  public Set<Configurable> getNameHits() {
    return myNameHits;
  }

  public Set<Configurable> getNameFullHits() {
    return myNameFullHit;
  }

  public Set<Configurable> getContentHits() {
    return myContentHits;
  }

  public Set<Configurable> getAll() {
    final LinkedHashSet<Configurable> all = new LinkedHashSet<>(myNameHits.size() + myContentHits.size());
    all.addAll(myNameHits);
    all.addAll(myContentHits);
    return all;
  }
}
