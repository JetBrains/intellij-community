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

package com.intellij.application.options.colors;

import com.intellij.application.options.OptionsContainingConfigurable;
import com.intellij.application.options.editor.EditorOptionsProvider;
import com.intellij.application.options.schemes.SchemesModel;
import com.intellij.codeHighlighting.RainbowHighlighter;
import com.intellij.execution.impl.ConsoleViewUtil;
import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.laf.LafManagerImpl;
import com.intellij.ide.ui.laf.darcula.DarculaInstaller;
import com.intellij.ide.ui.laf.darcula.DarculaLookAndFeelInfo;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.editor.colors.*;
import com.intellij.openapi.editor.colors.ex.DefaultColorSchemesManager;
import com.intellij.openapi.editor.colors.impl.*;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.options.*;
import com.intellij.openapi.options.colors.*;
import com.intellij.openapi.options.ex.Settings;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusFactory;
import com.intellij.packageDependencies.DependencyValidationManager;
import com.intellij.packageDependencies.DependencyValidationManagerImpl;
import com.intellij.psi.codeStyle.DisplayPriority;
import com.intellij.psi.codeStyle.DisplayPrioritySortable;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;
import com.intellij.psi.search.scope.packageSet.PackageSet;
import com.intellij.ui.ColorUtil;
import com.intellij.util.EventDispatcher;
import com.intellij.util.ui.UIUtil;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.Function;

import static com.intellij.openapi.actionSystem.CommonDataKeys.PROJECT;
import static com.intellij.openapi.actionSystem.PlatformDataKeys.CONTEXT_COMPONENT;

public class ColorAndFontOptions extends SearchableConfigurable.Parent.Abstract
  implements EditorOptionsProvider, SchemesModel<EditorColorsScheme> {
  public static final String ID = "reference.settingsdialog.IDE.editor.colors";
  public static final String FONT_CONFIGURABLE_NAME = "Color Scheme Font";

  private Map<String, MyColorScheme> mySchemes;
  private MyColorScheme mySelectedScheme;

  public static final String FILE_STATUS_GROUP = ApplicationBundle.message("title.file.status");
  public static final String SCOPES_GROUP = ApplicationBundle.message("title.scope.based");

  private boolean mySomeSchemesDeleted = false;
  private Map<ColorAndFontPanelFactory, InnerSearchableConfigurable> mySubPanelFactories;

  private SchemesPanel myRootSchemesPanel;

  private boolean myInitResetCompleted = false;
  private boolean myInitResetInvoked = false;

  private boolean myRevertChangesCompleted = false;

  private boolean myApplyCompleted = false;
  private boolean myDisposeCompleted = false;
  private final Disposable myDisposable = Disposer.newDisposable();

  private final EventDispatcher<ColorAndFontSettingsListener> myDispatcher = EventDispatcher.create(ColorAndFontSettingsListener.class);

  public void addListener(@NotNull ColorAndFontSettingsListener listener) {
    myDispatcher.addListener(listener);
  }

  public void stateChanged() {
    myDispatcher.getMulticaster().settingsChanged();
  }

  @Override
  public boolean isModified() {
    boolean listModified = isSchemeListModified();
    boolean schemeModified = isSomeSchemeModified();

    if (listModified || schemeModified) {
      myApplyCompleted = false;
    }

    return listModified;
  }

  private boolean isSchemeListModified() {
    if (mySomeSchemesDeleted) return true;

    if (!mySelectedScheme.getName().equals(EditorColorsManager.getInstance().getGlobalScheme().getName())) return true;

    for (MyColorScheme scheme : mySchemes.values()) {
      if (scheme.isNew()) return true;
    }

    return false;
  }

  private boolean isSomeSchemeModified() {
    for (MyColorScheme scheme : mySchemes.values()) {
      if (scheme.isModified()) return true;
    }
    return false;
  }

  public EditorColorsScheme selectScheme(@NotNull String name) {
    mySelectedScheme = getScheme(name);
    return mySelectedScheme;
  }

  MyColorScheme getScheme(String name) {
    return mySchemes.get(name);
  }

  public EditorColorsScheme getSelectedScheme() {
    return mySelectedScheme;
  }

  public EditorSchemeAttributeDescriptor[] getCurrentDescriptions() {
    return mySelectedScheme.getDescriptors();
  }

  @Override
  public boolean canDuplicateScheme(@NotNull EditorColorsScheme scheme) {
    return true;
  }

  @Override
  public boolean canResetScheme(@NotNull EditorColorsScheme scheme) {
    AbstractColorsScheme originalScheme =
      scheme instanceof AbstractColorsScheme ? ((AbstractColorsScheme)scheme).getOriginal() : null;
    return
      !isReadOnly(scheme) &&
      scheme.getName().startsWith(SchemeManager.EDITABLE_COPY_PREFIX) &&
      originalScheme instanceof ReadOnlyColorsScheme;
  }

  @Override
  public boolean canDeleteScheme(@NotNull EditorColorsScheme scheme) {
    return !isReadOnly(scheme) && canBeDeleted(scheme);
  }

  @Override
  public boolean isProjectScheme(@NotNull EditorColorsScheme scheme) {
    return false;
  }

  @Override
  public boolean canRenameScheme(@NotNull EditorColorsScheme scheme) {
    return canDeleteScheme(scheme);
  }

  @Override
  public boolean containsScheme(@NotNull String name, boolean projectScheme) {
    assert !projectScheme;
    return mySchemes.get(name) != null || mySchemes.get(SchemeManager.EDITABLE_COPY_PREFIX + name) != null;
  }

  @Override
  public boolean differsFromDefault(@NotNull EditorColorsScheme scheme) {
    if (scheme.getName().startsWith(SchemeManager.EDITABLE_COPY_PREFIX)) {
      String displayName = SchemeManager.getDisplayName(scheme);
      EditorColorsScheme defaultScheme = DefaultColorSchemesManager.getInstance().getScheme(displayName);
      if (defaultScheme == null) {
        defaultScheme = EditorColorsManager.getInstance().getScheme(displayName);
      }
      if (defaultScheme != null && scheme instanceof AbstractColorsScheme) {
        return !((AbstractColorsScheme)scheme).settingsEqual(defaultScheme);
      }
    }
    return false;
  }

  public static boolean isReadOnly(@NotNull final EditorColorsScheme scheme) {
    return ((MyColorScheme)scheme).isReadOnly();
  }

  public static boolean canBeDeleted(@NotNull final EditorColorsScheme scheme) {
    return scheme instanceof  MyColorScheme && ((MyColorScheme)scheme).canBeDeleted();
  }

  @NotNull
  public Collection<EditorColorsScheme> getOrderedSchemes() {
    List<EditorColorsScheme> schemes = new ArrayList<>(mySchemes.values());
    Collections.sort(schemes, EditorColorSchemesComparator.INSTANCE);
    return schemes;
  }

  @NotNull
  public Collection<EditorColorsScheme> getSchemes() {
    return new ArrayList<>(mySchemes.values());
  }

  public boolean saveSchemeAs(@NotNull EditorColorsScheme editorScheme, @NotNull String name) {
    if (editorScheme instanceof MyColorScheme) {
      MyColorScheme scheme = (MyColorScheme)editorScheme;
      EditorColorsScheme clone = (EditorColorsScheme)scheme.getParentScheme().clone();
      scheme.apply(clone);
      if (clone instanceof AbstractColorsScheme) {
        ((AbstractColorsScheme)clone).setSaveNeeded(true);
      }

      clone.setName(name);
      MyColorScheme newScheme = new MyColorScheme(clone);
      initScheme(newScheme);

      newScheme.setIsNew();

      mySchemes.put(name, newScheme);
      selectScheme(newScheme.getName());
      resetSchemesCombo(null);
      return true;
    }
    return false;
  }

  public void addImportedScheme(@NotNull EditorColorsScheme imported) {
    if (imported instanceof AbstractColorsScheme) ((AbstractColorsScheme)imported).setSaveNeeded(true);
    MyColorScheme newScheme = new MyColorScheme(imported);
    initScheme(newScheme);

    mySchemes.put(imported.getName(), newScheme);
    selectScheme(newScheme.getName());
    resetSchemesCombo(null);
  }

  @Override
  public void removeScheme(@NotNull EditorColorsScheme scheme) {
    String schemeName = scheme.getName();
    if (mySelectedScheme.getName().equals(schemeName)) {
      selectDefaultScheme();
    }

    boolean deletedNewlyCreated = false;

    MyColorScheme toDelete = mySchemes.get(schemeName);

    if (toDelete != null) {
      deletedNewlyCreated = toDelete.isNew();
    }

    mySchemes.remove(schemeName);
    resetSchemesCombo(null);
    mySomeSchemesDeleted = mySomeSchemesDeleted || !deletedNewlyCreated;
  }

  private void selectDefaultScheme() {
    DefaultColorsScheme defaultScheme =
      (DefaultColorsScheme)EditorColorsManager.getInstance().getScheme(EditorColorsManager.DEFAULT_SCHEME_NAME);
    selectScheme(defaultScheme.getEditableCopyName());
  }


  void resetSchemeToOriginal(@NotNull String name) {
    MyColorScheme schemeToReset = mySchemes.get(name);
    schemeToReset.resetToOriginal();
    resetImpl();
    selectScheme(name);
    resetSchemesCombo(null);
    ((EditorColorsManagerImpl)EditorColorsManager.getInstance()).schemeChangedOrSwitched(null);
  }

  @Override
  public void apply() throws ConfigurationException {
    if (myApplyCompleted) {
      return;
    }

    try {
      EditorColorsManager myColorsManager = EditorColorsManager.getInstance();
      SchemeManager<EditorColorsScheme> schemeManager = ((EditorColorsManagerImpl)myColorsManager).getSchemeManager();

      List<EditorColorsScheme> result = new ArrayList<>(mySchemes.values().size());
      boolean activeSchemeModified = false;
      EditorColorsScheme activeOriginalScheme = mySelectedScheme.getParentScheme();
      for (MyColorScheme scheme : mySchemes.values()) {
        boolean isModified = scheme.apply();
        if (isModified && !activeSchemeModified && activeOriginalScheme == scheme.getParentScheme()) {
          activeSchemeModified = true;
        }
        result.add(scheme.getParentScheme());
      }

      // refresh only if scheme is not switched
      boolean refreshEditors = activeSchemeModified && schemeManager.getCurrentScheme() == activeOriginalScheme;
      schemeManager.setSchemes(includingInvisible(result, schemeManager), activeOriginalScheme);
      if (refreshEditors) {
        ((EditorColorsManagerImpl)EditorColorsManager.getInstance()).schemeChangedOrSwitched(null);
      }

      final boolean isEditorThemeDark = ColorUtil.isDark(activeOriginalScheme.getDefaultBackground());
      changeLafIfNecessary(isEditorThemeDark);

      reset();
    }
    finally {
      myApplyCompleted = true;
    }
  }

  private static List<EditorColorsScheme> includingInvisible(@NotNull List<EditorColorsScheme> schemeList,
                                                             @NotNull SchemeManager<EditorColorsScheme> schemeManager) {
    for (EditorColorsScheme scheme : schemeManager.getAllSchemes()) {
      if (!AbstractColorsScheme.isVisible(scheme)) {
        schemeList.add(scheme);
      }
    }
    return schemeList;
  }

  private static void changeLafIfNecessary(boolean isDarkEditorTheme) {
    String propKey = "change.laf.on.editor.theme.change";
    String value = PropertiesComponent.getInstance().getValue(propKey);
    if ("false".equals(value)) return;
    boolean applyAlways = "true".equals(value);
    DialogWrapper.DoNotAskOption doNotAskOption = new DialogWrapper.DoNotAskOption.Adapter() {
      @Override
      public void rememberChoice(boolean isSelected, int exitCode) {
        if (isSelected) {
          PropertiesComponent.getInstance().setValue(propKey, Boolean.toString(exitCode == Messages.YES));
        }
      }

      @Override
      public boolean shouldSaveOptionsOnCancel() {
        return true;
      }
    };

    final String productName = ApplicationNamesInfo.getInstance().getFullProductName();
    final LafManager lafManager = LafManager.getInstance();
    if (isDarkEditorTheme && !UIUtil.isUnderDarcula()) {
      if (applyAlways || Messages.showYesNoDialog(
        "Looks like you have set a dark editor theme. Would you like to set dark theme for entire " + productName,
        "Change " + productName + " theme", Messages.YES_BUTTON, Messages.NO_BUTTON,
        Messages.getQuestionIcon(), doNotAskOption) == Messages.YES) {
        lafManager.setCurrentLookAndFeel(new DarculaLookAndFeelInfo());
        lafManager.updateUI();
        //noinspection SSBasedInspection
        SwingUtilities.invokeLater(DarculaInstaller::install);
      }
    } else if (!isDarkEditorTheme && UIUtil.isUnderDarcula()) {

      if (lafManager instanceof LafManagerImpl
          &&
          (applyAlways || Messages.showYesNoDialog(
            "Looks like you have set a bright editor theme. Would you like to set bright theme for entire " + productName,
            "Change " + productName + " theme", Messages.YES_BUTTON, Messages.NO_BUTTON,
            Messages.getQuestionIcon(), doNotAskOption) == Messages.YES)) {
        lafManager.setCurrentLookAndFeel(((LafManagerImpl)lafManager).getDefaultLaf());
        lafManager.updateUI();
        //noinspection SSBasedInspection
        SwingUtilities.invokeLater(DarculaInstaller::uninstall);
      }
    }
  }

  private boolean myIsReset = false;

  private void resetSchemesCombo(Object source) {
    myIsReset = true;
    try {
      myRootSchemesPanel.resetSchemesCombo(source);
      if (mySubPanelFactories != null) {
        for (NewColorAndFontPanel subPartialConfigurable : getPanels()) {
          subPartialConfigurable.reset(source);
        }
      }
    }
    finally {
      myIsReset = false;
    }
  }

  @Override
  public JComponent createComponent() {
    if (myRootSchemesPanel == null) {
      ensureSchemesPanel();
    }
    return myRootSchemesPanel;
  }

  @Override
  public boolean hasOwnContent() {
    return true;
  }

  @NotNull
  @Override
  public Configurable[] buildConfigurables() {
    myDisposeCompleted = false;
    initAll();

    List<ColorAndFontPanelFactory> panelFactories = createPanelFactories();

    List<Configurable> result = new ArrayList<>();
    mySubPanelFactories = new LinkedHashMap<>(panelFactories.size());
    for (ColorAndFontPanelFactory panelFactory : panelFactories) {
      mySubPanelFactories.put(panelFactory, new InnerSearchableConfigurable(panelFactory));
    }

    result.addAll(new ArrayList<SearchableConfigurable>(mySubPanelFactories.values()));
    return result.toArray(new Configurable[result.size()]);
  }

  @NotNull
  private Set<NewColorAndFontPanel> getPanels() {
    Set<NewColorAndFontPanel> result = new HashSet<>();
    for (InnerSearchableConfigurable configurable : mySubPanelFactories.values()) {
      NewColorAndFontPanel panel = configurable.getSubPanelIfInitialized();
      if (panel != null) {
        result.add(panel);
      }
    }
    return result;
  }

  protected List<ColorAndFontPanelFactory> createPanelFactories() {
    List<ColorAndFontPanelFactory> result = new ArrayList<>();

    List<ColorAndFontPanelFactory> extensions = new ArrayList<>();
    extensions.add(new FontConfigurableFactory());
    extensions.add(new ConsoleFontConfigurableFactory());
    ColorSettingsPage[] pages = ColorSettingsPages.getInstance().getRegisteredPages();
    for (final ColorSettingsPage page : pages) {
      extensions.add(new ColorAndFontPanelFactoryEx() {
        @Override
        @NotNull
        public NewColorAndFontPanel createPanel(@NotNull ColorAndFontOptions options) {
          final SimpleEditorPreview preview = new SimpleEditorPreview(options, page);
          return NewColorAndFontPanel.create(preview, page.getDisplayName(), options, null, page);
        }

        @Override
        @NotNull
        public String getPanelDisplayName() {
          return page.getDisplayName();
        }

        @Override
        public DisplayPriority getPriority() {
          if (page instanceof DisplayPrioritySortable) {
            return ((DisplayPrioritySortable)page).getPriority();
          }
          return DisplayPriority.LANGUAGE_SETTINGS;
        }
      });
    }
    Collections.addAll(extensions, Extensions.getExtensions(ColorAndFontPanelFactory.EP_NAME));
    Collections.sort(extensions, (f1, f2) -> {
      if (f1 instanceof DisplayPrioritySortable) {
        if (f2 instanceof DisplayPrioritySortable) {
          int result1 = ((DisplayPrioritySortable)f1).getPriority().compareTo(((DisplayPrioritySortable)f2).getPriority());
          if (result1 != 0) return result1;
        }
        else {
          return 1;
        }
      }
      else if (f2 instanceof DisplayPrioritySortable) {
        return -1;
      }
      return f1.getPanelDisplayName().compareToIgnoreCase(f2.getPanelDisplayName());
    });
    result.addAll(extensions);

    result.add(new FileStatusColorsPageFactory());
    result.add(new ScopeColorsPageFactory());

    return result;
  }

  private static class FontConfigurableFactory implements ColorAndFontPanelFactoryEx {
    @Override
    @NotNull
    public NewColorAndFontPanel createPanel(@NotNull ColorAndFontOptions options) {
      FontEditorPreview previewPanel = new FontEditorPreview(()->options.getSelectedScheme(), true);
      return new NewColorAndFontPanel(new SchemesPanel(options, 0), new FontOptions(options), previewPanel, FONT_CONFIGURABLE_NAME, null, null){
        @Override
        public boolean containsFontOptions() {
          return true;
        }
      };
    }

    @Override
    @NotNull
    public String getPanelDisplayName() {
      return FONT_CONFIGURABLE_NAME;
    }

    @Override
    public DisplayPriority getPriority() {
      return DisplayPriority.FONT_SETTINGS;
    }
  }

   private static class ConsoleFontConfigurableFactory implements ColorAndFontPanelFactoryEx {
    @Override
    @NotNull
    public NewColorAndFontPanel createPanel(@NotNull ColorAndFontOptions options) {
      FontEditorPreview previewPanel = new FontEditorPreview(()->options.getSelectedScheme(), false) {
        @Override
        protected EditorColorsScheme updateOptionsScheme(EditorColorsScheme selectedScheme) {
          return ConsoleViewUtil.updateConsoleColorScheme(selectedScheme);
        }
      };
      return new NewColorAndFontPanel(new SchemesPanel(options, 0), new ConsoleFontOptions(options), previewPanel, "Font", null, null){
        @Override
        public boolean containsFontOptions() {
          return true;
        }
      };
    }

    @Override
    @NotNull
    public String getPanelDisplayName() {
      return "Console Font";
    }

     @NotNull
     @Override
     public DisplayPriority getPriority() {
       return DisplayPriority.FONT_SETTINGS;
     }
   }

  private void initAll() {
    mySchemes = new THashMap<>();
    for (EditorColorsScheme allScheme : EditorColorsManager.getInstance().getAllSchemes()) {
      MyColorScheme schemeDelegate = new MyColorScheme(allScheme);
      initScheme(schemeDelegate);
      mySchemes.put(schemeDelegate.getName(), schemeDelegate);
    }

    mySelectedScheme = mySchemes.get(EditorColorsManager.getInstance().getGlobalScheme().getName());
    assert mySelectedScheme != null : EditorColorsManager.getInstance().getGlobalScheme().getName() + "; myschemes=" + mySchemes;
  }

  private static void initScheme(@NotNull MyColorScheme scheme) {
    List<EditorSchemeAttributeDescriptor> descriptions = new ArrayList<>();
    initPluggedDescriptions(descriptions, scheme);
    EditorColorsScheme original = scheme.getOriginal();
    if (original != null && original instanceof  DefaultColorsScheme) {
      initFileStatusDescriptors(descriptions, scheme);
    }
    initScopesDescriptors(descriptions, scheme);

    scheme.setDescriptors(descriptions.toArray(new EditorSchemeAttributeDescriptor[descriptions.size()]));
  }

  private static void initPluggedDescriptions(@NotNull List<EditorSchemeAttributeDescriptor> descriptions,
                                              @NotNull MyColorScheme scheme) {
    ColorSettingsPage[] pages = ColorSettingsPages.getInstance().getRegisteredPages();
    for (ColorSettingsPage page : pages) {
      initDescriptions(page, descriptions, scheme);
    }
    for (ColorAndFontDescriptorsProvider provider : Extensions.getExtensions(ColorAndFontDescriptorsProvider.EP_NAME)) {
      initDescriptions(provider, descriptions, scheme);
    }
  }

  private static void initDescriptions(@NotNull ColorAndFontDescriptorsProvider provider,
                                       @NotNull List<EditorSchemeAttributeDescriptor> descriptions,
                                       @NotNull MyColorScheme scheme) {
    String group = provider.getDisplayName();
    List<AttributesDescriptor> attributeDescriptors = ColorSettingsUtil.getAllAttributeDescriptors(provider);
    if (provider instanceof RainbowColorSettingsPage) {
      descriptions.add(new RainbowAttributeDescriptor(((RainbowColorSettingsPage)provider).getLanguage(),
                                                      group,
                                                      ApplicationBundle.message("rainbow.option.panel.display.name"),
                                                      scheme,
                                                      scheme.myRainbowState));
    }
    for (AttributesDescriptor descriptor : attributeDescriptors) {
      addSchemedDescription(descriptions, descriptor.getDisplayName(), group, descriptor.getKey(), scheme, null, null);
    }

    ColorDescriptor[] colorDescriptors = provider.getColorDescriptors();
    for (ColorDescriptor descriptor : colorDescriptors) {
      ColorKey back = descriptor.getKind() == ColorDescriptor.Kind.BACKGROUND ? descriptor.getKey() : null;
      ColorKey fore = descriptor.getKind() == ColorDescriptor.Kind.FOREGROUND ? descriptor.getKey() : null;
      addEditorSettingDescription(descriptions, descriptor.getDisplayName(), group, back, fore, scheme);
    }
  }

  private static void initFileStatusDescriptors(@NotNull List<EditorSchemeAttributeDescriptor> descriptions, MyColorScheme scheme) {

    FileStatus[] statuses = FileStatusFactory.getInstance().getAllFileStatuses();

    for (FileStatus fileStatus : statuses) {
      addEditorSettingDescription(descriptions,
                                  fileStatus.getText(),
                                  FILE_STATUS_GROUP,
                                  null,
                                  fileStatus.getColorKey(),
                                  scheme);

    }
  }
  private static void initScopesDescriptors(@NotNull List<EditorSchemeAttributeDescriptor> descriptions, @NotNull MyColorScheme scheme) {
    Set<Pair<NamedScope,NamedScopesHolder>> namedScopes = new THashSet<>(new TObjectHashingStrategy<Pair<NamedScope, NamedScopesHolder>>() {
      @Override
      public int computeHashCode(@NotNull final Pair<NamedScope, NamedScopesHolder> object) {
        return object.getFirst().getName().hashCode();
      }

      @Override
      public boolean equals(@NotNull final Pair<NamedScope, NamedScopesHolder> o1, @NotNull final Pair<NamedScope, NamedScopesHolder> o2) {
        return o1.getFirst().getName().equals(o2.getFirst().getName());
      }
    });
    Project[] projects = ProjectManager.getInstance().getOpenProjects();
    for (Project project : projects) {
      DependencyValidationManagerImpl validationManager = (DependencyValidationManagerImpl)DependencyValidationManager.getInstance(project);
      List<Pair<NamedScope,NamedScopesHolder>> cachedScopes = validationManager.getScopeBasedHighlightingCachedScopes();
      namedScopes.addAll(cachedScopes);
    }

    List<Pair<NamedScope, NamedScopesHolder>> list = new ArrayList<>(namedScopes);

    Collections.sort(list, (o1, o2) -> o1.getFirst().getName().compareToIgnoreCase(o2.getFirst().getName()));
    for (Pair<NamedScope,NamedScopesHolder> pair : list) {
      NamedScope namedScope = pair.getFirst();
      String name = namedScope.getName();
      TextAttributesKey textAttributesKey = ScopeAttributesUtil.getScopeTextAttributeKey(name);
      if (scheme.getAttributes(textAttributesKey) == null) {
        scheme.setAttributes(textAttributesKey, new TextAttributes());
      }
      NamedScopesHolder holder = pair.getSecond();

      PackageSet value = namedScope.getValue();
      String toolTip = holder.getDisplayName() + (value==null ? "" : ": "+ value.getText());
      addSchemedDescription(descriptions,
                            name,
                            SCOPES_GROUP,
                            textAttributesKey,
                            scheme, holder.getIcon(), toolTip);
    }
  }

  @Nullable
  private static String calcType(@Nullable ColorKey backgroundKey, @Nullable ColorKey foregroundKey) {
    if (foregroundKey != null) {
      return foregroundKey.getExternalName();
    }
    else if (backgroundKey != null) {
      return backgroundKey.getExternalName();
    }
    return null;
  }

  private static void addEditorSettingDescription(@NotNull List<EditorSchemeAttributeDescriptor> list,
                                                  String name,
                                                  String group,
                                                  @Nullable ColorKey backgroundKey,
                                                  @Nullable ColorKey foregroundKey,
                                                  @NotNull EditorColorsScheme scheme) {
    list.add(new EditorSettingColorDescription(name, group, backgroundKey, foregroundKey, calcType(backgroundKey, foregroundKey), scheme));
  }

  private static void addSchemedDescription(@NotNull List<EditorSchemeAttributeDescriptor> list,
                                            String name,
                                            String group,
                                            @NotNull TextAttributesKey key,
                                            @NotNull MyColorScheme scheme,
                                            Icon icon,
                                            String toolTip) {
    list.add(new SchemeTextAttributesDescription(name, group, key, scheme, icon, toolTip));
  }

  @Override
  public String getDisplayName() {
    return ApplicationBundle.message("title.colors.and.fonts");
  }

  private void revertChanges(){
    if (isSchemeListModified() || isSomeSchemeModified()) {
      myRevertChangesCompleted = false;
    }

    if (!myRevertChangesCompleted) {
      ensureSchemesPanel();


      try {
        resetImpl();
      }
      finally {
        myRevertChangesCompleted = true;
      }
    }

  }

  private void resetImpl() {
    mySomeSchemesDeleted = false;
    initAll();
    resetSchemesCombo(null);
  }

  @Override
  public synchronized void reset() {
    if (!myInitResetInvoked) {
      try {
        if (!myInitResetCompleted) {
          ensureSchemesPanel();

          try {
            resetImpl();
          }
          finally {
            myInitResetCompleted = true;
          }
        }
      }
      finally {
        myInitResetInvoked = true;
      }
    }
    else {
      revertChanges();
    }
  }

  public synchronized void resetFromChild() {
    if (!myInitResetCompleted) {
      ensureSchemesPanel();


      try {
        resetImpl();
      }
      finally {
        myInitResetCompleted = true;
      }
    }

  }

  private void ensureSchemesPanel() {
    if (myRootSchemesPanel == null) {
      myRootSchemesPanel = new SchemesPanel(this);

      myRootSchemesPanel.addListener(new ColorAndFontSettingsListener.Abstract(){
        @Override
        public void schemeChanged(final Object source) {
          if (!myIsReset) {
            resetSchemesCombo(source);
          }
        }
      });

    }
  }

  @Override
  public void disposeUIResources() {
    try {
      if (!myDisposeCompleted) {
        try {
          super.disposeUIResources();
          Disposer.dispose(myDisposable);
        }
        finally {
          myDisposeCompleted = true;
        }
      }
    }
    finally {
      mySubPanelFactories = null;

      myInitResetCompleted = false;
      myInitResetInvoked = false;
      myRevertChangesCompleted = false;

      myApplyCompleted = false;
      myRootSchemesPanel = null;
    }
  }

  private static class SchemeTextAttributesDescription extends TextAttributesDescription {
    @NotNull private final TextAttributes myInitialAttributes;
    @NotNull private final TextAttributesKey key;
    private TextAttributes myFallbackAttributes;
    private Pair<ColorSettingsPage,AttributesDescriptor> myBaseAttributeDescriptor;
    private boolean myIsInheritedInitial = false;

    private SchemeTextAttributesDescription(String name, String group, @NotNull TextAttributesKey key, @NotNull MyColorScheme  scheme, Icon icon,
                                           String toolTip) {
      super(name, group,
            getInitialAttributes(scheme, key).clone(),
            key, scheme, icon, toolTip);
      this.key = key;
      myInitialAttributes = getInitialAttributes(scheme, key);
      TextAttributesKey fallbackKey = key.getFallbackAttributeKey();
      if (fallbackKey != null) {
        myFallbackAttributes = scheme.getAttributes(fallbackKey);
        myBaseAttributeDescriptor = ColorSettingsPages.getInstance().getAttributeDescriptor(fallbackKey);
        if (myBaseAttributeDescriptor == null) {
          myBaseAttributeDescriptor =
            new Pair<>(null, new AttributesDescriptor(fallbackKey.getExternalName(), fallbackKey));
        }
      }
      myIsInheritedInitial = scheme.isInherited(key);
      setInherited(myIsInheritedInitial);
      if (myIsInheritedInitial) {
        setInheritedAttributes(getTextAttributes());
      }
      initCheckedStatus();
    }


    private void setInheritedAttributes(@NotNull TextAttributes attributes) {
      attributes.setFontType(myFallbackAttributes.getFontType());
      attributes.setForegroundColor(myFallbackAttributes.getForegroundColor());
      attributes.setBackgroundColor(myFallbackAttributes.getBackgroundColor());
      attributes.setErrorStripeColor(myFallbackAttributes.getErrorStripeColor());
      attributes.setEffectColor(myFallbackAttributes.getEffectColor());
      attributes.setEffectType(myFallbackAttributes.getEffectType());
    }


    @NotNull
    private static TextAttributes getInitialAttributes(@NotNull MyColorScheme scheme, @NotNull TextAttributesKey key) {
      TextAttributes attributes = scheme.getAttributes(key);
      return attributes != null ? attributes : new TextAttributes();
    }

    @Override
    public void apply(EditorColorsScheme scheme) {
      if (scheme == null) {
        scheme = getScheme();
      }

      if (scheme instanceof EditorColorsSchemeImpl) {
        if (!isInherited()) {
          scheme.setAttributes(key, getTextAttributes());
        }
        else if (!myIsInheritedInitial) {
          // set only if previously was not inherited (and, so, we must mark it as inherited)
          // https://youtrack.jetbrains.com/issue/IDEA-162844
          scheme.setAttributes(key, USE_INHERITED_MARKER);
        }
      }
      else {
        scheme.setAttributes(key, isInherited() ? USE_INHERITED_MARKER : getTextAttributes());
      }
    }

    @Override
    public boolean isModified() {
      if (isInherited()) {
        return !myIsInheritedInitial;
      }
      return !Comparing.equal(myInitialAttributes, getTextAttributes()) || myIsInheritedInitial;
    }

    @Override
    public boolean isErrorStripeEnabled() {
      return true;
    }

    @Nullable
    @Override
    public TextAttributes getBaseAttributes() {
      return myFallbackAttributes;
    }

    @Nullable
    @Override
    public Pair<ColorSettingsPage,AttributesDescriptor> getBaseAttributeDescriptor() {
      return myBaseAttributeDescriptor;
    }
  }

  private static class GetSetColor {
    private final ColorKey myKey;
    private final EditorColorsScheme myScheme;
    private final Color myInitialColor;
    private Color myColor;

    private GetSetColor(ColorKey key, EditorColorsScheme scheme) {
      myKey = key;
      myScheme = scheme;
      myColor = myScheme.getColor(myKey);
      myInitialColor = myColor;
    }

    public Color getColor() {
      return myColor;
    }

    public void setColor(Color col) {
      if (getColor() == null || !getColor().equals(col)) {
        myColor = col;
      }
    }

    public void apply(EditorColorsScheme scheme) {
      if (scheme == null) scheme = myScheme;
      scheme.setColor(myKey, myColor);
    }

    public boolean isModified() {
      return !Comparing.equal(myColor, myInitialColor);
    }
  }

  private static class EditorSettingColorDescription extends ColorAndFontDescription {
    private GetSetColor myGetSetForeground;
    private GetSetColor myGetSetBackground;

    private EditorSettingColorDescription(String name,
                                         String group,
                                         ColorKey backgroundKey,
                                         ColorKey foregroundKey,
                                         String type,
                                         EditorColorsScheme scheme) {
      super(name, group, type, scheme, null, null);
      if (backgroundKey != null) {
        myGetSetBackground = new GetSetColor(backgroundKey, scheme);
      }
      if (foregroundKey != null) {
        myGetSetForeground = new GetSetColor(foregroundKey, scheme);
      }
      initCheckedStatus();
    }

    @Override
    public int getFontType() {
      return Font.PLAIN;
    }

    @Override
    public void setFontType(int type) {
    }

    @Override
    public Color getExternalEffectColor() {
      return null;
    }

    @Override
    public void setExternalEffectColor(Color color) {
    }

    @Override
    public void setExternalEffectType(EffectType type) {
    }

    @NotNull
    @Override
    public EffectType getExternalEffectType() {
      return EffectType.LINE_UNDERSCORE;
    }

    @Override
    public Color getExternalForeground() {
      if (myGetSetForeground == null) {
        return null;
      }
      return myGetSetForeground.getColor();
    }

    @Override
    public void setExternalForeground(Color col) {
      if (myGetSetForeground == null) {
        return;
      }
      myGetSetForeground.setColor(col);
    }

    @Override
    public Color getExternalBackground() {
      if (myGetSetBackground == null) {
        return null;
      }
      return myGetSetBackground.getColor();
    }

    @Override
    public void setExternalBackground(Color col) {
      if (myGetSetBackground == null) {
        return;
      }
      myGetSetBackground.setColor(col);
    }

    @Override
    public Color getExternalErrorStripe() {
      return null;
    }

    @Override
    public void setExternalErrorStripe(Color col) {
    }

    @Override
    public boolean isFontEnabled() {
      return false;
    }

    @Override
    public boolean isForegroundEnabled() {
      return myGetSetForeground != null;
    }

    @Override
    public boolean isBackgroundEnabled() {
      return myGetSetBackground != null;
    }

    @Override
    public boolean isEffectsColorEnabled() {
      return false;
    }

    @Override
    public boolean isModified() {
      return myGetSetBackground != null && myGetSetBackground.isModified()
             || myGetSetForeground != null && myGetSetForeground.isModified();
    }

    @Override
    public void apply(EditorColorsScheme scheme) {
      if (myGetSetBackground != null) {
        myGetSetBackground.apply(scheme);
      }
      if (myGetSetForeground != null) {
        myGetSetForeground.apply(scheme);
      }
    }
  }

  @Override
  @NotNull
  public String getHelpTopic() {
    return ID;
  }

  private static class MyColorScheme extends EditorColorsSchemeImpl {
    private EditorSchemeAttributeDescriptor[] myDescriptors;
    private String                            myName;
    private boolean myIsNew = false;
    private RainbowColorsInSchemeState myRainbowState;

    private MyColorScheme(@NotNull EditorColorsScheme parentScheme) {
      super(parentScheme);

      if (parentScheme.isUseEditorFontPreferencesInConsole()) {
        setUseEditorFontPreferencesInConsole();
      }
      else {
        setConsoleFontPreferences(parentScheme.getConsoleFontPreferences());
      }

      if (parentScheme.isUseAppFontPreferencesInEditor()) {
        setUseAppFontPreferencesInEditor();
      }
      else {
        setFontPreferences(parentScheme.getFontPreferences());
      }

      setQuickDocFontSize(parentScheme.getQuickDocFontSize());
      myName = parentScheme.getName();

      RainbowHighlighter.transferRainbowState(this, parentScheme);
      myRainbowState = new RainbowColorsInSchemeState(this, parentScheme);

      initFonts();
    }

    @Nullable
    @Override
    public AbstractColorsScheme getOriginal() {
      return myParentScheme instanceof AbstractColorsScheme ? ((AbstractColorsScheme)myParentScheme).getOriginal() : null;
    }

    @NotNull
    @Override
    public String getName() {
      return myName;
    }

    @Override
    public void setName(@NotNull String name) {
      myName = name;
    }

    public void setDescriptors(EditorSchemeAttributeDescriptor[] descriptors) {
      myDescriptors = descriptors;
    }

    public EditorSchemeAttributeDescriptor[] getDescriptors() {
      return myDescriptors;
    }

    @Override
    public boolean isReadOnly() {
      return myParentScheme instanceof ReadOnlyColorsScheme;
    }

    public boolean isModified() {
      if (isFontModified() || isConsoleFontModified()) return true;

      for (EditorSchemeAttributeDescriptor descriptor : myDescriptors) {
        if (descriptor.isModified()) {
          return true;
        }
      }

      return false;
    }

    @Override
    public boolean canBeDeleted() {
      return (myParentScheme instanceof AbstractColorsScheme) && ((AbstractColorsScheme)myParentScheme).canBeDeleted();
    }

    private boolean isFontModified() {
      return !areDelegatingOrEqual(getFontPreferences(), myParentScheme.getFontPreferences());
    }

    private boolean isConsoleFontModified() {
      return !areDelegatingOrEqual(getConsoleFontPreferences(), myParentScheme.getConsoleFontPreferences());
    }

    private boolean apply() {
      if (!(myParentScheme instanceof ReadOnlyColorsScheme)) {
        return apply(myParentScheme);
      }
      return false;
    }

    private boolean apply(@NotNull EditorColorsScheme scheme) {
      boolean isModified = isFontModified() || isConsoleFontModified();

      if (isUseAppFontPreferencesInEditor()) {
        scheme.setUseAppFontPreferencesInEditor();
      }
      else {
        scheme.setFontPreferences(getFontPreferences());
      }

      if (isUseEditorFontPreferencesInConsole()) {
        scheme.setUseEditorFontPreferencesInConsole();
      }
      else {
        scheme.setConsoleFontPreferences(getConsoleFontPreferences());
      }

      for (EditorSchemeAttributeDescriptor descriptor : myDescriptors) {
        if (descriptor.isModified()) {
          isModified = true;
          descriptor.apply(scheme);
        }
      }

      if (isModified && scheme instanceof AbstractColorsScheme) {
        ((AbstractColorsScheme)scheme).setSaveNeeded(true);
      }
      return isModified;
    }

    @Override
    public Object clone() {
      return null;
    }

    public void setIsNew() {
      myIsNew = true;
    }

    public boolean isNew() {
      return myIsNew;
    }

    @NotNull
    @Override
    public String toString() {
      return "temporary scheme for " + myName;
    }

    public boolean isInherited(TextAttributesKey key) {
      TextAttributesKey fallbackKey = key.getFallbackAttributeKey();
      if (fallbackKey != null) {
        if (myParentScheme instanceof AbstractColorsScheme) {
          TextAttributes ownAttrs = ((AbstractColorsScheme)myParentScheme).getDirectlyDefinedAttributes(key);
          if (ownAttrs != null) {
            return ownAttrs == TextAttributes.USE_INHERITED_MARKER;
          }
        }
        TextAttributes attributes = getAttributes(key);
        if (attributes != null) {
          TextAttributes fallbackAttributes = getAttributes(fallbackKey);
          return attributes == fallbackAttributes;
        }
      }
      return false;
    }

    public void resetToOriginal() {
      if (myParentScheme instanceof AbstractColorsScheme) {
        AbstractColorsScheme originalScheme = ((AbstractColorsScheme)myParentScheme).getOriginal();
        if (originalScheme != null) {
          originalScheme.copyTo((AbstractColorsScheme)myParentScheme);
          ((AbstractColorsScheme)myParentScheme).setSaveNeeded(true);
        }
      }
    }
  }

  @Override
  @NotNull
  public String getId() {
    return getHelpTopic();
  }

  @Nullable
  public SearchableConfigurable findSubConfigurable(@NotNull Class pageClass) {
    if (mySubPanelFactories == null) {
      getConfigurables();
    }
    for (Map.Entry<ColorAndFontPanelFactory, InnerSearchableConfigurable> entry : mySubPanelFactories.entrySet()) {
      if (pageClass.isInstance(entry.getValue().createPanel().getSettingsPage())) {
        return entry.getValue();
      }
    }
    return null;
  }

  @Nullable
  public SearchableConfigurable findSubConfigurable(String pageName) {
    if (mySubPanelFactories == null) {
      buildConfigurables();
    }
    for (InnerSearchableConfigurable configurable : mySubPanelFactories.values()) {
      if (configurable.getDisplayName().equals(pageName)) {
        return configurable;
      }
    }
    return null;
  }

  @Nullable
  public NewColorAndFontPanel findPage(String pageName) {
    InnerSearchableConfigurable child = (InnerSearchableConfigurable)findSubConfigurable(pageName);
    return child == null ? null : child.createPanel();
  }

  private class InnerSearchableConfigurable implements SearchableConfigurable, OptionsContainingConfigurable, NoScroll {
    private NewColorAndFontPanel mySubPanel;
    private boolean mySubInitInvoked = false;
    @NotNull private final ColorAndFontPanelFactory myFactory;

    private InnerSearchableConfigurable(@NotNull ColorAndFontPanelFactory factory) {
      myFactory = factory;
    }

    @NotNull
    @Override
    @Nls
    public String getDisplayName() {
      return myFactory.getPanelDisplayName();
    }

    public NewColorAndFontPanel getSubPanelIfInitialized() {
      return mySubPanel;
    }

    private NewColorAndFontPanel createPanel() {
      if (mySubPanel == null) {
        mySubPanel = myFactory.createPanel(ColorAndFontOptions.this);
        mySubPanel.reset(this);
        mySubPanel.addSchemesListener(new ColorAndFontSettingsListener.Abstract(){
          @Override
          public void schemeChanged(final Object source) {
            if (!myIsReset) {
              resetSchemesCombo(source);
            }
          }
        });

        mySubPanel.addDescriptionListener(new ColorAndFontSettingsListener.Abstract(){
          @Override
          public void fontChanged() {
            for (NewColorAndFontPanel panel : getPanels()) {
              panel.updatePreview();
              panel.updateSchemesPanel();
            }
          }
        });
      }
      return mySubPanel;
    }

    @Override
    public String getHelpTopic() {
      return null;
    }

    @Override
    public JComponent createComponent() {
      return createPanel().getPanel();
    }

    @Override
    public boolean isModified() {
      createPanel();
      for (MyColorScheme scheme : mySchemes.values()) {
        if (mySubPanel.containsFontOptions()) {
          if (scheme.isFontModified() || scheme.isConsoleFontModified()) {
            myRevertChangesCompleted = false;
            return true;
          }
        }
        else {
          for (EditorSchemeAttributeDescriptor descriptor : scheme.getDescriptors()) {
            if (mySubPanel.contains(descriptor) && descriptor.isModified()) {
              myRevertChangesCompleted = false;
              return true;
            }
          }
        }

      }

      return false;

    }

    @Override
    public void apply() throws ConfigurationException {
      ColorAndFontOptions.this.apply();
    }

    @Override
    public void reset() {
      if (!mySubInitInvoked) {
        if (!myInitResetCompleted) {
          resetFromChild();
        }
        mySubInitInvoked = true;
      }
      else {
        revertChanges();
      }
    }

    @Override
    public void disposeUIResources() {
      if (mySubPanel != null) {
        mySubPanel.disposeUIResources();
        mySubPanel = null;
      }
    }

    @Override
    @NotNull
    public String getId() {
      return ColorAndFontOptions.this.getId() + "." + getDisplayName();
    }

    @Override
    public Runnable enableSearch(final String option) {
      return createPanel().showOption(option);
    }

    @NotNull
    @Override
    public Set<String> processListOptions() {
      return createPanel().processListOptions();
    }

    @NotNull
    @NonNls
    @Override
    public String toString() {
      return "Color And Fonts for "+getDisplayName();
    }
  }

  /**
   * Shows a requested page to edit a color settings.
   * If current data context represents a setting dialog that can open a requested page,
   * it will be opened. Otherwise, the new dialog will be opened.
   * The simplest way to get a data context is
   * <pre>DataManager.getInstance().getDataContext(myComponent)</pre>
   * where is {@code myComponent} is a {@link JComponent} in a Swing hierarchy.
   * A specific color can be requested by the {@code search} text.
   *
   * @param context a data context to find {@link Settings} or a parent for dialog
   * @param search  a text to find on the found page
   * @param name    a name of a page to find via {@link #findSubConfigurable(String)}
   * @return {@code true} if a color was shown to edit, {@code false} if a requested page does not exist
   */
  public static boolean selectOrEditColor(@NotNull DataContext context, @Nullable String search, @NotNull String name) {
    return selectOrEdit(context, search, options -> options.findSubConfigurable(name));
  }

  /**
   * Shows a requested page to edit a color settings.
   * If current data context represents a setting dialog that can open a requested page,
   * it will be opened. Otherwise, the new dialog will be opened.
   * The simplest way to get a data context is
   * <pre>DataManager.getInstance().getDataContext(myComponent)</pre>
   * where is {@code myComponent} is a {@link JComponent} in a Swing hierarchy.
   * A specific color can be requested by the {@code search} text.
   *
   * @param context a data context to find {@link Settings} or a parent for dialog
   * @param search  a text to find on the found page
   * @param type    a type of a page to find via {@link #findSubConfigurable(Class)}
   * @return {@code true} if a color was shown to edit, {@code false} if a requested page does not exist
   */
  public static boolean selectOrEditColor(@NotNull DataContext context, @Nullable String search, @NotNull Class<?> type) {
    return selectOrEdit(context, search, options -> options.findSubConfigurable(type));
  }

  private static boolean selectOrEdit(DataContext context, String search, Function<ColorAndFontOptions, SearchableConfigurable> function) {
    return select(context, search, function) || edit(context, search, function);
  }

  private static boolean select(DataContext context, String search, Function<ColorAndFontOptions, SearchableConfigurable> function) {
    Settings settings = Settings.KEY.getData(context);
    if (settings == null) return false;

    ColorAndFontOptions options = settings.find(ColorAndFontOptions.class);
    if (options == null) return false;

    SearchableConfigurable page = function.apply(options);
    if (page == null) return false;

    settings.select(page, search);
    return true;
  }

  private static boolean edit(DataContext context, String search, Function<ColorAndFontOptions, SearchableConfigurable> function) {
    ColorAndFontOptions options = new ColorAndFontOptions();
    SearchableConfigurable page = function.apply(options);

    Configurable[] configurables = options.getConfigurables();
    try {
      if (page != null) {
        Runnable runnable = search == null ? null : page.enableSearch(search);
        Window window = UIUtil.getWindow(CONTEXT_COMPONENT.getData(context));
        if (window != null) {
          ShowSettingsUtil.getInstance().editConfigurable(window, page, runnable);
        }
        else {
          ShowSettingsUtil.getInstance().editConfigurable(PROJECT.getData(context), page, runnable);
        }
      }
    }
    finally {
      for (Configurable configurable : configurables) configurable.disposeUIResources();
      options.disposeUIResources();
    }
    return page != null;
  }
}
