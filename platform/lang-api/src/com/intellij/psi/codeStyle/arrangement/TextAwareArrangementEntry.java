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
package com.intellij.psi.codeStyle.arrangement;

import org.jetbrains.annotations.Nullable;

/**
 * Stands for the {@link ArrangementEntry} which provides information about its text.
 * E.g. this entry can be used for section start/end element to match section comments by whole text.
 * <p/>
 * Implementations of this interface are not obliged to be thread-safe.
 *
 * @author Svetlana Zemlyanskaya
 * @since 25/04/14 08:17 PM
 */
public interface TextAwareArrangementEntry extends ArrangementEntry {
  @Nullable
  String getText();
}
