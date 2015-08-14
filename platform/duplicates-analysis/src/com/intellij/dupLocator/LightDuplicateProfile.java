/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.dupLocator;

import com.intellij.lang.LighterAST;
import com.intellij.lang.LighterASTNode;
import org.jetbrains.annotations.NotNull;

/**
 * Created by Maxim.Mossienko on 10/1/2014.
 */
public interface LightDuplicateProfile {
  void process(@NotNull LighterAST ast, @NotNull Callback callback);

  interface Callback {
    void process(int hash, int hash2, @NotNull LighterAST ast, @NotNull LighterASTNode... nodes);
  }
}
