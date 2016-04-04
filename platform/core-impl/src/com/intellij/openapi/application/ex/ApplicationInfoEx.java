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
package com.intellij.openapi.application.ex;

import com.intellij.openapi.application.ApplicationInfo;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Calendar;
import java.util.List;

/**
 * @author mike
 * @since Sep 16, 2002
 */
public abstract class ApplicationInfoEx extends ApplicationInfo {
  public static ApplicationInfoEx getInstanceEx() {
    return (ApplicationInfoEx) getInstance();
  }

  public abstract Calendar getMajorReleaseBuildDate();

  public abstract String getSplashImageUrl();

  public abstract Color getSplashTextColor();

  public abstract String getAboutImageUrl();

  public abstract String getIconUrl();

  public abstract String getSmallIconUrl();

  public abstract String getBigIconUrl();

  public abstract String getToolWindowIconUrl();

  public abstract String getWelcomeScreenLogoUrl();

  public abstract String getEditorBackgroundImageUrl();

  /**
   * This method is used to detect that the product isn't meant to be used as an IDE but is embedded to another product or used as a
   * standalone tool so different licensing scheme should be applied.
   */
  @Nullable
  public abstract String getPackageCode();

  public abstract String getFullApplicationName();

  public abstract boolean showLicenseeInfo();

  public abstract boolean isEAP();

  public abstract UpdateUrls getUpdateUrls();

  public abstract String getDocumentationUrl();

  public abstract String getSupportUrl();

  public abstract String getEAPFeedbackUrl();

  public abstract String getReleaseFeedbackUrl();

  public abstract String getPluginManagerUrl();

  public abstract String getPluginsListUrl();

  public abstract String getChannelsListUrl();

  public abstract String getPluginsDownloadUrl();

  public abstract String getBuiltinPluginsUrl();

  public abstract String getWebHelpUrl();

  public abstract String getWhatsNewUrl();

  public abstract String getWinKeymapUrl();

  public abstract String getMacKeymapUrl();

  public abstract Color getAboutForeground();

  public interface UpdateUrls {
    String getCheckingUrl();
    String getPatchesUrl();
  }

  public interface PluginChooserPage {
    String getTitle();
    String getCategory();
    String getDependentPlugin();
  }

  public abstract List<PluginChooserPage> getPluginChooserPages();

  /**
   * @return {@code true} if the specified plugin is an essential part of the IDE so it cannot be disabled and isn't shown in Settings | Plugins
   */
  public abstract boolean isEssentialPlugin(String pluginId);

  @Nullable
  public abstract String getCustomizeIDEWizardStepsProvider();
}
