// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui;

import com.intellij.ide.GeneralSettings;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.ui.search.BooleanOptionDescription;
import com.intellij.ide.ui.search.OptionDescription;
import com.intellij.ui.IdeUICustomization;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import static com.intellij.ide.ui.OptionsTopHitProvider.messageIde;

/**
 * @author Sergey.Malenkov
 */
final class SystemOptionsTopHitProvider implements OptionsTopHitProvider.ApplicationLevelProvider {
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
  public Collection<OptionDescription> getOptions() {
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
