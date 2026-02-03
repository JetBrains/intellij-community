/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.psi.PsiElement;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;

/**
 * Listener for Highlighting settings modified from the 'Hector Inspector' slider.
 * Doesn't get called during the initial settings load
 */
public interface FileHighlightingSettingListener {
  Topic<FileHighlightingSettingListener> SETTING_CHANGE = Topic.create("File Highlighting Setting", FileHighlightingSettingListener.class);

  /**
   * @param root element in the PSI tree for which the settings has changed
   * @param setting new value for the highlighting level
   */
  void settingChanged(@NotNull PsiElement root, @NotNull FileHighlightingSetting setting);
}
