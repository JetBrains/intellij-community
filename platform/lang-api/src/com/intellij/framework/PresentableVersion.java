/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.framework;

import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Avdeev
 */
public interface PresentableVersion {

  /** Full version name as presented in combos, like JavaEE 6 */
  @NotNull
  String getPresentableName();

  /** Just version number, like 2.2.1 */
  String getVersionNumber();
}
