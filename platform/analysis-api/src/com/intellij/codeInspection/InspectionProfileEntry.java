// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInspection.ex.InspectionElementsMerger;
import com.intellij.configurationStore.XmlSerializer;
import com.intellij.diagnostic.PluginException;
import com.intellij.lang.Language;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.templateLanguages.TemplateLanguageFileViewProvider;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.serialization.SerializationException;
import com.intellij.util.ResourceUtil;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashingStrategy;
import com.intellij.util.xmlb.SerializationFilter;
import com.intellij.util.xmlb.annotations.Property;
import org.jdom.Element;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Property(assertIfNoBindings = false)
public abstract class InspectionProfileEntry implements BatchSuppressableTool {
  private static final Logger LOG = Logger.getInstance(InspectionProfileEntry.class);

  private static Set<String> ourBlackList;
  private static final Object BLACK_LIST_LOCK = new Object();
  private Boolean myUseNewSerializer;

  /**
   * For global tools read-only, for local tools would be used instead getID for modules with alternative classpath storage
   */
  @NonNls
  public @Nullable String getAlternativeID() {
    return null;
  }

  @Override
  public boolean isSuppressedFor(@NotNull PsiElement element) {
    Set<InspectionSuppressor> suppressors = getSuppressors(element);
    return !suppressors.isEmpty() && isSuppressedFor(element, suppressors);
  }

  private boolean isSuppressedFor(@NotNull PsiElement element, Set<? extends InspectionSuppressor> suppressors) {
    String toolId = getSuppressId();
    for (InspectionSuppressor suppressor : suppressors) {
      if (isSuppressed(toolId, suppressor, element)) {
        return true;
      }
    }

    InspectionElementsMerger merger = InspectionElementsMerger.getMerger(getShortName());
    return merger != null && isSuppressedForMerger(element, suppressors, merger);
  }

  private static boolean isSuppressedForMerger(PsiElement element, Set<? extends InspectionSuppressor> suppressors, InspectionElementsMerger merger) {
    String[] suppressIds = merger.getSuppressIds();
    String[] sourceToolIds = suppressIds.length != 0 ? suppressIds : merger.getSourceToolNames();
    for (String sourceToolId : sourceToolIds) {
      for (InspectionSuppressor suppressor : suppressors) {
        if (suppressor.isSuppressedFor(element, sourceToolId)) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Tool ID passed to {@link InspectionSuppressor}.
   */
  @NonNls
  protected @NotNull String getSuppressId() {
    return getShortName();
  }

  @Override
  public SuppressQuickFix @NotNull [] getBatchSuppressActions(@Nullable PsiElement element) {
    if (element == null) {
      return SuppressQuickFix.EMPTY_ARRAY;
    }
    Set<SuppressQuickFix> fixes = CollectionFactory.createCustomHashingStrategySet(new HashingStrategy<>() {
      @Override
      public int hashCode(@Nullable SuppressQuickFix object) {
        if (object == null) {
          return 0;
        }
        int result = object instanceof InjectionAwareSuppressQuickFix
                     ? ((InjectionAwareSuppressQuickFix)object).isShouldBeAppliedToInjectionHost().hashCode()
                     : 0;
        return 31 * result + object.getName().hashCode();
      }

      @Override
      public boolean equals(SuppressQuickFix o1, SuppressQuickFix o2) {
        if (o1 == o2) {
          return true;
        }
        if (o1 == null || o2 == null) {
          return false;
        }

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

  public static @NotNull Set<InspectionSuppressor> getSuppressors(@NotNull PsiElement element) {
    PsiUtilCore.ensureValid(element);
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

  public void initialize(@NotNull GlobalInspectionContext context) {
  }

  interface DefaultNameProvider {

    @NonNls
    @Nullable
    String getDefaultShortName();

    /**
     * Unlocalized inspection group name
     */
    @NonNls
    @Nullable
    String getGroupKey();

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @Nullable
    String getDefaultDisplayName();

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @Nullable
    String getDefaultGroupDisplayName();
  }

  protected volatile DefaultNameProvider myNameProvider;

  /**
   * @see InspectionEP#groupDisplayName
   * @see InspectionEP#groupKey
   * @see InspectionEP#groupBundle
   */
  public @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String getGroupDisplayName() {
    if (myNameProvider != null) {
      final String name = myNameProvider.getDefaultGroupDisplayName();
      if (name != null) {
        return name;
      }
    }
    PluginException.logPluginError(LOG, getClass() + ": group display name should be overridden or configured via XML ", null, getClass());
    return "";
  }

  /**
   * @see InspectionEP#groupKey
   */
  @NonNls
  public @Nullable String getGroupKey() {
    if (myNameProvider != null) {
      return myNameProvider.getGroupKey();
    }
    return null;
  }

  /**
   * @see InspectionEP#groupPath
   */
  public @Nls(capitalization = Nls.Capitalization.Sentence) String @NotNull [] getGroupPath() {
    String groupDisplayName = getGroupDisplayName();
    if (groupDisplayName.isEmpty()) {
      groupDisplayName = getGeneralGroupName();
    }
    return new String[]{groupDisplayName};
  }

  /**
   * @see InspectionEP#displayName
   * @see InspectionEP#key
   * @see InspectionEP#bundle
   */
  public @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String getDisplayName() {
    if (myNameProvider != null) {
      final String name = myNameProvider.getDefaultDisplayName();
      if (name != null) {
        return name;
      }
    }
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      PluginException.logPluginError(LOG, getClass() + ": display name should be overridden or configured via XML ", null, getClass());
    }
    return "";
  }

  /**
   * DO NOT OVERRIDE this method.
   *
   * @see InspectionEP#shortName
   */
  @NonNls
  public @NotNull String getShortName() {
    if (myNameProvider != null) {
      final String name = myNameProvider.getDefaultShortName();
      if (name != null) {
        return name;
      }
    }
    return getShortName(getClass().getSimpleName());
  }

  public static @NotNull String getShortName(@NotNull String className) {
    return StringUtil.trimEnd(StringUtil.trimEnd(className, "Inspection"), "InspectionBase");
  }

  /**
   * DO NOT OVERRIDE this method.
   *
   * @see InspectionEP#level
   */
  public @NotNull HighlightDisplayLevel getDefaultLevel() {
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
   * To get correct spacing, return a JComponent with empty insets using Kotlin UI DSL
   * or {@link com.intellij.codeInspection.ui.InspectionOptionsPanel}.
   *
   * @return {@code null} if no UI options required.
   */
  public @Nullable JComponent createOptionsPanel() {
    return null;
  }

  /**
   * @return true iff default configuration options should be shown for the tool. E.g. scope-severity settings.
   * @apiNote if {@code false} returned, only panel provided by {@link #createOptionsPanel()} is shown if any.
   */
  public boolean showDefaultConfigurationOptions() {
    return true;
  }

  /**
   * Read in settings from XML config.
   * Default implementation uses XmlSerializer so you may use public fields (like {@code int TOOL_OPTION})
   * and bean-style getters/setters (like {@code int getToolOption(), void setToolOption(int)}) to store your options.
   *
   * @param node to read settings from.
   * @throws InvalidDataException if the loaded data was not valid.
   */
  public void readSettings(@NotNull Element node) {
    if (useNewSerializer()) {
      try {
        XmlSerializer.deserializeInto(node, this);
      }
      catch (SerializationException e) {
        throw new InvalidDataException(e);
      }
    }
    else {
      //noinspection deprecation
      DefaultJDOMExternalizer.readExternal(this, node);
    }
  }

  /**
   * Store current settings in XML config.
   * Default implementation uses XmlSerializer so you may use public fields (like {@code int TOOL_OPTION})
   * and bean-style getters/setters (like {@code int getToolOption(), void setToolOption(int)}) to store your options.
   *
   * @param node to store settings to.
   */
  public void writeSettings(@NotNull Element node) {
    if (useNewSerializer()) {
      XmlSerializer.serializeObjectInto(this, node, getSerializationFilter());
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
    ourBlackList = new HashSet<>();

    URL url = InspectionProfileEntry.class.getResource("inspection-black-list.txt");
    if (url == null) {
      LOG.error("Resource not found");
      return;
    }

    try (BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream(), StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        line = line.trim();
        if (!line.isEmpty()) {
          ourBlackList.add(line);
        }
      }
    }
    catch (IOException e) {
      LOG.error("Unable to load resource: " + url, e);
    }
  }

  public static @NotNull Collection<String> getBlackList() {
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
  @SuppressWarnings("DeprecatedIsStillUsed")
  @Deprecated(forRemoval = true)
  protected @Nullable SerializationFilter getSerializationFilter() {
    return XmlSerializer.getJdomSerializer().getDefaultSerializationFilter();
  }

  /**
   * Override this method to return a HTML inspection description. Otherwise it will be loaded from resources using ID.
   *
   * @return hard-coded inspection description.
   */
  public @Nullable @Nls String getStaticDescription() {
    return null;
  }

  public @Nullable String getDescriptionFileName() {
    return null;
  }

  protected @NotNull Class<? extends InspectionProfileEntry> getDescriptionContextClass() {
    return getClass();
  }

  public boolean isInitialized() {
    return true;
  }

  /**
   * @return short name of tool whose results will be used
   */
  @NonNls
  public @Nullable String getMainToolId() {
    return null;
  }

  public @Nullable @Nls String loadDescription() {
    final String description = getStaticDescription();
    if (description != null) return description;

    try {
      InputStream descriptionStream = null;
      final String fileName = getDescriptionFileName();
      if (fileName != null) {
        descriptionStream =
          ResourceUtil.getResourceAsStream(getDescriptionContextClass().getClassLoader(), "inspectionDescriptions", fileName);
      }
      //noinspection HardCodedStringLiteral(IDEA-249976)
      return descriptionStream != null ? ResourceUtil.loadText(descriptionStream) : null;
    }
    catch (IOException ignored) {
    }

    return null;
  }

  public static @Nls String getGeneralGroupName() {
    return InspectionsBundle.message("inspection.general.tools.group.name");
  }
}
