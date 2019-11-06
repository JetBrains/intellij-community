// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.AbstractBundle;
import com.intellij.CommonBundle;
import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.diagnostic.PluginException;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageExtensionPoint;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ResourceBundle;

/**
 * @author Dmitry Avdeev
 * @see LocalInspectionEP
 */
public class InspectionEP extends LanguageExtensionPoint<InspectionProfileEntry> implements InspectionProfileEntry.DefaultNameProvider {
  private static final Logger LOG = Logger.getInstance(InspectionEP.class);

  /**
   * @see GlobalInspectionTool
   */
  public static final ExtensionPointName<InspectionEP> GLOBAL_INSPECTION = ExtensionPointName.create("com.intellij.globalInspection");

  /**
   * Usually generated automatically from FQN.
   * Must be unique among all inspections.
   * <p>
   * Short name is used in two cases: {@code \inspectionDescriptions\<short_name>.html} resource may contain short inspection
   * description to be shown in "Inspect Code..." dialog and also provide some file name convention when using offline
   * inspection or export to HTML function.
   */
  @Attribute("shortName")
  public String shortName;

  @NonNls
  @NotNull
  public String getShortName() {
    if (implementationClass == null) {
      throw new IllegalArgumentException(toString());
    }
    return shortName == null ? shortName = InspectionProfileEntry.getShortName(StringUtil.getShortName(implementationClass)) : shortName;
  }

  @Nls(capitalization = Nls.Capitalization.Sentence)
  @Nullable
  public String getDisplayName() {
    return displayName == null ? displayName = getLocalizedString(bundle, key) : displayName;
  }

  @Nls(capitalization = Nls.Capitalization.Sentence)
  @Nullable
  public String getGroupDisplayName() {
    return groupDisplayName == null ? groupDisplayName = getLocalizedString(groupBundle, groupKey) : groupDisplayName;
  }


  @Override
  @Nullable
  public String getGroupKey() {
    return groupKey;
  }
  /**
   * Message key for {@link #displayName}.
   *
   * @see #bundle
   */
  @Attribute("key")
  @Nls(capitalization = Nls.Capitalization.Sentence)
  public String key;

  /**
   * Message bundle, e.g. {@code "messages.InspectionsBundle"}.
   * If unspecified, plugin's {@code <resource-bundle>} is used.
   *
   * @see #key
   */
  @Attribute("bundle")
  public String bundle;

  /**
   * Non-localized display name used in UI (Settings|Editor|Inspections and "Inspection Results" tool window). Use {@link #key} for I18N.
   */
  @Attribute("displayName")
  @Nls(capitalization = Nls.Capitalization.Sentence)
  public String displayName;

  /**
   * Message key for {@link #groupDisplayName}.
   *
   * @see #groupBundle
   */
  @Attribute("groupKey")
  @Nls(capitalization = Nls.Capitalization.Sentence)
  public String groupKey;

  /**
   * Message bundle, e.g. {@code "messages.InspectionsBundle"}.
   * If unspecified, will use {@link #bundle}, then plugin's {@code <resource-bundle>} as fallback.
   *
   * @see #groupKey
   */
  @Attribute("groupBundle")
  public String groupBundle;

  /**
   * Non-localized group display name used in UI (Settings|Editor|Inspections). Use {@link #groupKey} for I18N.
   */
  @Attribute("groupName")
  @Nls(capitalization = Nls.Capitalization.Sentence)
  public String groupDisplayName;

  /**
   * Comma-delimited list of parent group names (excluding {@code groupName}) used in UI (Settings|Editor|Inspections), e.g. {@code "Java,Java language level migration aids"}.
   */
  @Attribute("groupPath")
  public String groupPath;

  @Nullable
  public String[] getGroupPath() {
    String name = getGroupDisplayName();
    if (name == null) return null;
    if (groupPath == null) {
      return new String[]{name.isEmpty() ? InspectionProfileEntry.GENERAL_GROUP_NAME : name};
    }
    return ArrayUtil.append(groupPath.split(","), name);
  }

  @Attribute("enabledByDefault")
  public boolean enabledByDefault;

  /**
   * Whether this inspection should be applied to dialects of specified language.
   *
   * @see Language#getDialects()
   */
  @Attribute("applyToDialects")
  public boolean applyToDialects = true;

  /**
   * If {@code true}, the inspection can run as part of the code cleanup action.
   */
  @Attribute("cleanupTool")
  public boolean cleanupTool;

  /**
   * Highlighting level for this inspection tool that is used in default settings, e.g. {@code "INFO", "ERROR"}.
   *
   * @see HighlightDisplayLevel
   */
  @Attribute("level")
  public String level;

  @NotNull
  public HighlightDisplayLevel getDefaultLevel() {
    if (level == null) return HighlightDisplayLevel.WARNING;
    HighlightDisplayLevel displayLevel = HighlightDisplayLevel.find(level);
    if (displayLevel == null) {
      LOG.error(new PluginException("Can't find highlight display level: " + level + "; registered for: " + implementationClass + "; " +
                                    "and short name: " + shortName, getPluginDescriptor().getPluginId()));
      return HighlightDisplayLevel.WARNING;
    }
    return displayLevel;
  }

  /**
   * Whether inspection's description should use {@link InspectionProfileEntry#getStaticDescription()}.
   */
  @Attribute("hasStaticDescription")
  public boolean hasStaticDescription;

  @Nullable
  private String getLocalizedString(@Nullable String bundleName, String key) {
    final String baseName = bundleName != null ? bundleName :
                            bundle == null ? getPluginDescriptor().getResourceBundleBaseName() : bundle;
    if (baseName == null || key == null) {
      if (bundleName != null) {
        LOG.warn(implementationClass);
      }
      return null;
    }
    ResourceBundle resourceBundle = AbstractBundle.getResourceBundle(baseName, getLoaderForClass());
    return CommonBundle.message(resourceBundle, key);
  }

  @NotNull
  public InspectionProfileEntry instantiateTool() {
    // must create a new instance for each invocation
    InspectionProfileEntry entry = createInstance(ApplicationManager.getApplication());
    entry.myNameProvider = this;
    return entry;
  }

  @Override
  public String getDefaultShortName() {
    return getShortName();
  }

  @Override
  public String getDefaultDisplayName() {
    return getDisplayName();
  }

  @Override
  public String getDefaultGroupDisplayName() {
    return getGroupDisplayName();
  }

  /**
   * @see com.intellij.codeInspection.ui.InspectionToolPresentation
   */
  @Attribute("presentation")
  public String presentation;

  /**
   * Do not show internal inspections if IDE internal mode is off.
   */
  @Attribute("isInternal")
  public boolean isInternal;

  @Override
  public String toString() {
    return "InspectionEP{" +
           "shortName='" + shortName + '\'' +
           ", key='" + key + '\'' +
           ", bundle='" + bundle + '\'' +
           ", displayName='" + displayName + '\'' +
           ", groupKey='" + groupKey + '\'' +
           ", groupBundle='" + groupBundle + '\'' +
           ", groupDisplayName='" + groupDisplayName + '\'' +
           ", groupPath='" + groupPath + '\'' +
           ", enabledByDefault=" + enabledByDefault +
           ", applyToDialects=" + applyToDialects +
           ", cleanupTool=" + cleanupTool +
           ", level='" + level + '\'' +
           ", hasStaticDescription=" + hasStaticDescription +
           ", presentation='" + presentation + '\'' +
           ", isInternal=" + isInternal +
           '}';
  }
}
