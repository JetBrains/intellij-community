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
package com.intellij.ide.ui;

import com.intellij.ide.GeneralSettings;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.ui.search.BooleanOptionDescription;
import com.intellij.ide.ui.search.OptionDescription;
import com.intellij.openapi.project.Project;
import com.intellij.ui.IdeUICustomization;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

/**
 * @author Sergey.Malenkov
 */
public final class SystemOptionsTopHitProvider extends OptionsTopHitProvider {
  private static final Collection<OptionDescription> ourOptions = Collections.unmodifiableCollection(Arrays.asList(
    option(messageIde("checkbox.show.tips.on.startup"), "showTipsOnStartup", "setShowTipsOnStartup"),
    option(IdeBundle.message("checkbox.reopen.last.project.on.startup", IdeUICustomization.getInstance().getProjectConceptName()), "isReopenLastProject", "setReopenLastProject"),
    option(messageIde("checkbox.support.screen.readers"), "isSupportScreenReaders", "setSupportScreenReaders"),
    option(messageIde("checkbox.confirm.application.exit"), "isConfirmExit", "setConfirmExit"),
    option(messageIde("checkbox.synchronize.files.on.frame.activation"), "isSyncOnFrameActivation", "setSyncOnFrameActivation"),
    option(messageIde("checkbox.save.files.on.frame.deactivation"), "isSaveOnFrameDeactivation", "setSaveOnFrameDeactivation"),
    option(messageIde("checkbox.save.files.automatically"), "isAutoSaveIfInactive", "setAutoSaveIfInactive"),
    option("Use \"safe write\" (save changes to a temporary file first)", "isUseSafeWrite", "setUseSafeWrite"),
    //option("Use default browser", "isUseDefaultBrowser", "setUseDefaultBrowser"),
    //option("Show confirmation before extracting files", "isConfirmExtractFiles", "setConfirmExtractFiles"),
    option("Start search in background", "isSearchInBackground", "setSearchInBackground")));

  @NotNull
  @Override
  public Collection<OptionDescription> getOptions(@Nullable Project project) {
    return ourOptions;
  }

  @Override
  public String getId() {
    return "system";
  }

  static BooleanOptionDescription option(String option, String getter, String setter) {
    return new PublicMethodBasedOptionDescription(option, "preferences.general", getter, setter) {
      @Override
      public Object getInstance() {
        return GeneralSettings.getInstance();
      }
    };
  }
}
