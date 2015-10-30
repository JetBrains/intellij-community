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
package com.intellij.ide.util.treeView;

import com.intellij.openapi.util.NamedRunnable;
import com.intellij.util.Consumer;

/**
 * @author Sergey.Malenkov
 */
abstract class TreeRunnable extends NamedRunnable {
  protected TreeRunnable(String name) {
    super(name);
  }

  protected abstract void perform();

  @Override
  public final void run() {
    debug("started");
    perform();
    debug("finished");
  }

  abstract static class TreeConsumer<T> extends TreeRunnable implements Consumer<T> {
    protected TreeConsumer(String name) {
      super(name);
    }

    @Override
    public final void consume(T t) {
      run();
    }
  }
}
