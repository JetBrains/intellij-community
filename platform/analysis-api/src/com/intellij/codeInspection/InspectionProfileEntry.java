/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.codeInspection.ex.InspectionElementsMerger;
import com.intellij.lang.Language;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.templateLanguages.TemplateLanguageFileViewProvider;
import com.intellij.util.ResourceUtil;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.SerializationFilter;
import com.intellij.util.xmlb.SkipDefaultValuesSerializationFilters;
import com.intellij.util.xmlb.XmlSerializationException;
import com.intellij.util.xmlb.XmlSerializer;
import gnu.trove.THashSet;
import gnu.trove.TObjectHashingStrategy;
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
import java.util.*;

/**
 * @author anna
 * @since 28-Nov-2005
 */
public abstract class InspectionProfileEntry implements BatchSuppressableTool {
  public static final String GENERAL_GROUP_NAME = InspectionsBundle.message("inspection.general.tools.group.name");

  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.InspectionProfileEntry");

  private static final SerializationFilter DEFAULT_FILTER = new SkipDefaultValuesSerializationFilters();
  private static Set<String> ourBlackList;
  private static final Object BLACK_LIST_LOCK = new Object();
  private Boolean myUseNewSerializer;

  /**
   * For global tools read-only, for local tools would be used instead getID for modules with alternative classpath storage
   */
  @NonNls
  @Nullable
  public String getAlternativeID() {
    return null;
  }

  @Override
  public boolean isSuppressedFor(@NotNull PsiElement element) {
    Set<InspectionSuppressor> suppressors = getSuppressors(element);
    String toolId = getSuppressId();
    for (InspectionSuppressor suppressor : suppressors) {
      if (isSuppressed(toolId, suppressor, element)) {
        return true;
      }
    }

    final InspectionElementsMerger merger = InspectionElementsMerger.getMerger(getShortName());
    if (merger != null) {
      String[] suppressIds = merger.getSuppressIds();
      String[] sourceToolIds = suppressIds.length != 0 ? suppressIds : merger.getSourceToolNames();
      for (String sourceToolId : sourceToolIds) {
        for (InspectionSuppressor suppressor : suppressors) {
          if (suppressor.isSuppressedFor(element, sourceToolId)) {
            return true;
          }
        }
      }
    }

    return false;
  }

  @NotNull
  protected String getSuppressId() {
    return getShortName();
  }

  @NotNull
  @Override
  public SuppressQuickFix[] getBatchSuppressActions(@Nullable PsiElement element) {
    if (element == null) {
      return SuppressQuickFix.EMPTY_ARRAY;
    }
    Set<SuppressQuickFix> fixes = new THashSet<>(new TObjectHashingStrategy<SuppressQuickFix>() {
      @Override
      public int computeHashCode(SuppressQuickFix object) {
        int result = object instanceof InjectionAwareSuppressQuickFix
                     ? ((InjectionAwareSuppressQuickFix)object).isShouldBeAppliedToInjectionHost().hashCode()
                     : 0;
        return 31 * result + object.getName().hashCode();
      }

      @Override
      public boolean equals(SuppressQuickFix o1, SuppressQuickFix o2) {
        if (o1 instanceof InjectionAwareSuppressQuickFix && o2 instanceof InjectionAwareSuppressQuickFix) {
          if (((InjectionAwareSuppressQuickFix)o1).isShouldBeAppliedToInjectionHost() !=
              ((InjectionAwareSuppressQuickFix)o2).isShouldBeAppliedToInjectionHost()) {
            return false;
          }
        }
        return o1.getName().equals(o2.getName());
      }
    });

    Set<InspectionSuppressor> suppressors = getSuppressors(element);
    final PsiLanguageInjectionHost injectionHost = InjectedLanguageManager.getInstance(element.getProject()).getInjectionHost(element);
    if (injectionHost != null) {
      Set<InspectionSuppressor> injectionHostSuppressors = getSuppressors(injectionHost);
      for (InspectionSuppressor suppressor : injectionHostSuppressors) {
        addAllSuppressActions(fixes, injectionHost, suppressor, ThreeState.YES, getSuppressId());
      }
    }

    for (InspectionSuppressor suppressor : suppressors) {
      addAllSuppressActions(fixes, element, suppressor, injectionHost != null ? ThreeState.NO : ThreeState.UNSURE, getSuppressId());
    }
    return fixes.toArray(SuppressQuickFix.EMPTY_ARRAY);
  }

  private static void addAllSuppressActions(@NotNull Collection<? super SuppressQuickFix> fixes,
                                            @NotNull PsiElement element,
                                            @NotNull InspectionSuppressor suppressor,
                                            @NotNull ThreeState appliedToInjectionHost,
                                            @NotNull String toolId) {
    final SuppressQuickFix[] actions = suppressor.getSuppressActions(element, toolId);
    for (SuppressQuickFix action : actions) {
      if (action instanceof InjectionAwareSuppressQuickFix) {
        ((InjectionAwareSuppressQuickFix)action).setShouldBeAppliedToInjectionHost(appliedToInjectionHost);
      }
      fixes.add(action);
    }
  }

  private boolean isSuppressed(@NotNull String toolId,
                               @NotNull InspectionSuppressor suppressor,
                               @NotNull PsiElement element) {
    if (suppressor.isSuppressedFor(element, toolId)) {
      return true;
    }
    final String alternativeId = getAlternativeID();
    return alternativeId != null && !alternativeId.equals(toolId) && suppressor.isSuppressedFor(element, alternativeId);
  }

  @NotNull
  public static Set<InspectionSuppressor> getSuppressors(@NotNull PsiElement element) {
    FileViewProvider viewProvider = element.getContainingFile().getViewProvider();
    final List<InspectionSuppressor> elementLanguageSuppressor = LanguageInspectionSuppressors.INSTANCE.allForLanguage(element.getLanguage());
    if (viewProvider instanceof TemplateLanguageFileViewProvider) {
      Set<InspectionSuppressor> suppressors = new LinkedHashSet<>();
      ContainerUtil.addAllNotNull(suppressors, LanguageInspectionSuppressors.INSTANCE.allForLanguage(viewProvider.getBaseLanguage()));
      for (Language language : viewProvider.getLanguages()) {
        ContainerUtil.addAllNotNull(suppressors, LanguageInspectionSuppressors.INSTANCE.allForLanguage(language));
      }
      ContainerUtil.addAllNotNull(suppressors, elementLanguageSuppressor);
      return suppressors;
    }
    if (!element.getLanguage().isKindOf(viewProvider.getBaseLanguage())) {
      // handling embedding elements {@link EmbeddingElementType
      Set<InspectionSuppressor> suppressors = new LinkedHashSet<>();
      ContainerUtil.addAllNotNull(suppressors, LanguageInspectionSuppressors.INSTANCE.allForLanguage(viewProvider.getBaseLanguage()));
      ContainerUtil.addAllNotNull(suppressors, elementLanguageSuppressor);
      return suppressors;
    }
    int size = elementLanguageSuppressor.size();
    switch (size) {
      case 0:
        return Collections.emptySet();
      case 1:
        return Collections.singleton(elementLanguageSuppressor.get(0));
      default:
        return new HashSet<>(elementLanguageSuppressor);
    }
  }

  public void cleanup(@NotNull Project project) {
  }

  interface DefaultNameProvider {
    @Nullable String getDefaultShortName();
    @Nullable String getDefaultDisplayName();
    @Nullable String getDefaultGroupDisplayName();
  }

  protected volatile DefaultNameProvider myNameProvider;

  /**
   * @see InspectionEP#groupDisplayName
   * @see InspectionEP#groupKey
   * @see InspectionEP#groupBundle
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
   * @see InspectionEP#groupPath
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
   * @see InspectionEP#displayName
   * @see InspectionEP#key
   * @see InspectionEP#bundle
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
   * @see InspectionEP#shortName
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
    return StringUtil.trimEnd(StringUtil.trimEnd(className, "Inspection"), "InspectionBase");
  }

  /**
   * DO NOT OVERRIDE this method.
   *
   * @see InspectionEP#level
   */
  @NotNull
  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.WARNING;
  }

  /**
   * DO NOT OVERRIDE this method.
   *
   * @see InspectionEP#enabledByDefault
   */
  public boolean isEnabledByDefault() {
    return false;
  }

  /**
   * This method is called each time UI is shown.
   *
   * @return null if no UI options required.
   */
  @Nullable
  public JComponent createOptionsPanel() {
    return null;
  }

  /**
   * Read in settings from XML config.
   * Default implementation uses XmlSerializer so you may use public fields (like {@code int TOOL_OPTION})
   * and bean-style getters/setters (like {@code int getToolOption(), void setToolOption(int)}) to store your options.
   *
   * @param node to read settings from.
   * @throws InvalidDataException if the loaded data was not valid.
   */
  @SuppressWarnings("deprecation")
  public void readSettings(@NotNull Element node) {
    if (useNewSerializer()) {
      try {
        XmlSerializer.deserializeInto(this, node);
      }
      catch (XmlSerializationException e) {
        throw new InvalidDataException(e);
      }
    }
    else {
      DefaultJDOMExternalizer.readExternal(this, node);
    }
  }

  /**
   * Store current settings in XML config.
   * Default implementation uses XmlSerializer so you may use public fields (like {@code int TOOL_OPTION})
   * and bean-style getters/setters (like {@code int getToolOption(), void setToolOption(int)}) to store your options.
   *
   * @param node to store settings to.
   * @throws WriteExternalException if no data should be saved for this component.
   */
  public void writeSettings(@NotNull Element node) {
    if (useNewSerializer()) {
      XmlSerializer.serializeInto(this, node, getSerializationFilter());
    }
    else {
      //noinspection deprecation
      DefaultJDOMExternalizer.writeExternal(this, node);
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

    try (BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()))) {
      String line;
      while ((line = reader.readLine()) != null) {
        line = line.trim();
        if (!line.isEmpty()) ourBlackList.add(line);
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
    catch (IOException ignored) {
    }

    return null;
  }
}
