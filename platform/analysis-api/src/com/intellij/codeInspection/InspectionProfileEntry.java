/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.codeInspection;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ResourceUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.SerializationFilter;
import com.intellij.util.xmlb.SkipDefaultValuesSerializationFilters;
import com.intellij.util.xmlb.XmlSerializationException;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Collection;
import java.util.Set;

/**
 * @author anna
 * @since 28-Nov-2005
 */
@SuppressWarnings("JavadocReference")
public abstract class InspectionProfileEntry {
  public static final String GENERAL_GROUP_NAME = InspectionsBundle.message("inspection.general.tools.group.name");

  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.InspectionProfileEntry");

  private static final SkipDefaultValuesSerializationFilters DEFAULT_FILTER = new SkipDefaultValuesSerializationFilters();
  private static Set<String> ourBlackList = null;
  private static final Object BLACK_LIST_LOCK = new Object();
  private Boolean myUseNewSerializer = null;

  public void cleanup(Project project) {

  }

  interface DefaultNameProvider {
    @Nullable String getDefaultShortName();
    @Nullable String getDefaultDisplayName();
    @Nullable String getDefaultGroupDisplayName();
  }

  protected volatile DefaultNameProvider myNameProvider = null;

  /**
   * @see com.intellij.codeInspection.InspectionEP#groupDisplayName
   * @see com.intellij.codeInspection.InspectionEP#groupKey
   * @see com.intellij.codeInspection.InspectionEP#groupBundle
   */
  @Nls
  @NotNull
  public String getGroupDisplayName() {
    if (myNameProvider != null) {
      final String name = myNameProvider.getDefaultGroupDisplayName();
      if (name != null) {
        return name;
      }
    }
    LOG.error(getClass() + ": group display name should be overridden or configured via XML " + getClass());
    return "";
  }

  /**
   * @see com.intellij.codeInspection.InspectionEP#groupPath
   */
  @NotNull
  public String[] getGroupPath() {
    String groupDisplayName = getGroupDisplayName();
    if (groupDisplayName.isEmpty()) {
      groupDisplayName = GENERAL_GROUP_NAME;
    }
    return new String[]{groupDisplayName};
  }

  /**
   * @see com.intellij.codeInspection.InspectionEP#displayName
   * @see com.intellij.codeInspection.InspectionEP#key
   * @see com.intellij.codeInspection.InspectionEP#bundle
   */
  @Nls
  @NotNull
  public String getDisplayName() {
    if (myNameProvider != null) {
      final String name = myNameProvider.getDefaultDisplayName();
      if (name != null) {
        return name;
      }
    }
    LOG.error(getClass() + ": display name should be overridden or configured via XML " + getClass());
    return "";
  }

  /**
   * DO NOT OVERRIDE this method.
   *
   * @see com.intellij.codeInspection.InspectionEP#shortName
   */
  @NonNls
  @NotNull
  public String getShortName() {
    if (myNameProvider != null) {
      final String name = myNameProvider.getDefaultShortName();
      if (name != null) {
        return name;
      }
    }
    return getShortName(getClass().getSimpleName());
  }

  @NotNull
  public static String getShortName(@NotNull String className) {
    return StringUtil.trimEnd(className, "Inspection");
  }

  /**
   * DO NOT OVERRIDE this method.
   *
   * @see com.intellij.codeInspection.InspectionEP#level
   */
  @NotNull
  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.WARNING;
  }

  /**
   * DO NOT OVERRIDE this method.
   *
   * @see com.intellij.codeInspection.InspectionEP#enabledByDefault
   */
  public boolean isEnabledByDefault() {
    return false;
  }

  /**
   * This method is called each time UI is shown.
   * @return null if no UI options required.
   */
  @Nullable
  public JComponent createOptionsPanel() {
    return null;
  }

  /**
   * Read in settings from XML config.
   * Default implementation uses XmlSerializer so you may use public fields (like <code>int TOOL_OPTION</code>)
   * and bean-style getters/setters (like <code>int getToolOption(), void setToolOption(int)</code>) to store your options.
   *
   * @param node to read settings from.
   * @throws InvalidDataException if the loaded data was not valid.
   */
  @SuppressWarnings("deprecation")
  public void readSettings(@NotNull Element node) throws InvalidDataException {
    if (useNewSerializer()) {
      try {
        XmlSerializer.deserializeInto(this, node);
      }
      catch (XmlSerializationException e) {
        throw new InvalidDataException(e);
      }
    }
    else {
      //noinspection UnnecessaryFullyQualifiedName
      com.intellij.openapi.util.DefaultJDOMExternalizer.readExternal(this, node);
    }
  }

  /**
   * Store current settings in XML config.
   * Default implementation uses XmlSerializer so you may use public fields (like <code>int TOOL_OPTION</code>)
   * and bean-style getters/setters (like <code>int getToolOption(), void setToolOption(int)</code>) to store your options.
   *
   * @param node to store settings to.
   * @throws WriteExternalException if no data should be saved for this component.
   */
  @SuppressWarnings("deprecation")
  public void writeSettings(@NotNull Element node) throws WriteExternalException {
    if (useNewSerializer()) {
      XmlSerializer.serializeInto(this, node, getSerializationFilter());
    }
    else {
      //noinspection UnnecessaryFullyQualifiedName
      com.intellij.openapi.util.DefaultJDOMExternalizer.writeExternal(this, node);
    }
  }

  private synchronized boolean useNewSerializer() {
    if (myUseNewSerializer == null) {
      myUseNewSerializer = !getBlackList().contains(getClass().getName());
    }
    return myUseNewSerializer;
  }

  private static void loadBlackList() {
    ourBlackList = ContainerUtil.newHashSet();

    final URL url = InspectionProfileEntry.class.getResource("inspection-black-list.txt");
    if (url == null) {
      LOG.error("Resource not found");
      return;
    }

    try {
      final BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()));
      try {
        String line;
        while ((line = reader.readLine()) != null) {
          line = line.trim();
          if (!line.isEmpty()) ourBlackList.add(line);
        }
      }
      finally {
        reader.close();
      }
    }
    catch (IOException e) {
      LOG.error("Unable to load resource: " + url, e);
    }
  }

  @NotNull
  public static Collection<String> getBlackList() {
    synchronized (BLACK_LIST_LOCK) {
      if (ourBlackList == null) {
        loadBlackList();
      }
      return ourBlackList;
    }
  }

  /**
   * Returns filter used to omit default values on saving inspection settings.
   * Default implementation uses SkipDefaultValuesSerializationFilters.
   *
   * @return serialization filter.
   */
  @SuppressWarnings("MethodMayBeStatic")
  @Nullable
  protected SerializationFilter getSerializationFilter() {
    return DEFAULT_FILTER;
  }

  /**
   * Initialize inspection with project. Is called on project opened for all profiles as well as on profile creation.
   *
   * @param project to be associated with this entry
   * @deprecated this won't work for inspections configured via {@link com.intellij.codeInspection.InspectionEP}
   */
  public void projectOpened(@NotNull Project project) {
  }

  /**
   * Cleanup inspection settings corresponding to the project. Is called on project closed for all profiles as well as on profile deletion.
   *
   * @param project to be disassociated from this entry
   * @deprecated this won't work for inspections configured via {@link com.intellij.codeInspection.InspectionEP}
   */
  public void projectClosed(@NotNull Project project) {
  }

  /**
   * Override this method to return a html inspection description. Otherwise it will be loaded from resources using ID.
   *
   * @return hard-code inspection description.
   */
  @Nullable
  public String getStaticDescription() {
    return null;
  }

  @Nullable
  public String getDescriptionFileName() {
    return null;
  }

  @Nullable
  protected URL getDescriptionUrl() {
    final String fileName = getDescriptionFileName();
    if (fileName == null) return null;
    return ResourceUtil.getResource(getDescriptionContextClass(), "/inspectionDescriptions", fileName);
  }

  @NotNull
  protected Class<? extends InspectionProfileEntry> getDescriptionContextClass() {
    return getClass();
  }

  public boolean isInitialized() {
    return true;
  }

  /**
   * @return short name of tool whose results will be used
   */
  @Nullable
  public String getMainToolId() {
    return null;
  }

  @Nullable
  public String loadDescription() {
    final String description = getStaticDescription();
    if (description != null) return description;

    try {
      URL descriptionUrl = getDescriptionUrl();
      if (descriptionUrl == null) return null;
      return ResourceUtil.loadText(descriptionUrl);
    }
    catch (IOException ignored) { }

    return null;
  }
}
