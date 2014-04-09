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
package com.intellij.ide.startupWizardV2;

import com.intellij.CommonBundle;
import org.jetbrains.annotations.NotNull;

public class FeaturedPluginsPage extends AbstractWizardPage{
  @NotNull
  @Override
  String getID() {
    return "Featured plugins";
  }

  @NotNull
  @Override
  String getTitle() {
    return "Download featured plugins";
  }

  @NotNull
  @Override
  String getHeader() {
    return "We have a few plugins in our web repository that most  users like to download. Perhaps, you need them too?";
  }

  @NotNull
  @Override
  String getFooter() {
    return "New plugins can be also downloaded in " + CommonBundle.settingsTitle() +" | Plugins";
  }
}
