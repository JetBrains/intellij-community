// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.colors;

import com.intellij.application.options.OptionsContainingConfigurable;
import com.intellij.application.options.editor.EditorOptionsProvider;
import com.intellij.application.options.schemes.SchemesModel;
import com.intellij.codeHighlighting.RainbowHighlighter;
import com.intellij.execution.impl.ConsoleViewUtil;
import com.intellij.ide.actions.QuickChangeColorSchemeAction;
import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.LafManagerListener;
import com.intellij.ide.ui.laf.UIThemeLookAndFeelInfoKt;
import com.intellij.internal.inspector.PropertyBean;
import com.intellij.internal.inspector.UiInspectorContextProvider;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.*;
import com.intellij.openapi.editor.colors.ex.DefaultColorSchemesManager;
import com.intellij.openapi.editor.colors.impl.AbstractColorsScheme;
import com.intellij.openapi.editor.colors.impl.DefaultColorsScheme;
import com.intellij.openapi.editor.colors.impl.EditorColorsManagerImpl;
import com.intellij.openapi.editor.colors.impl.EditorColorsSchemeImpl;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.extensions.BaseExtensionPointName;
import com.intellij.openapi.options.*;
import com.intellij.openapi.options.colors.*;
import com.intellij.openapi.options.ex.Settings;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vcs.FileStatusFactory;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.packageDependencies.DependencyValidationManager;
import com.intellij.packageDependencies.DependencyValidationManagerImpl;
import com.intellij.psi.codeStyle.DisplayPriority;
import com.intellij.psi.codeStyle.DisplayPrioritySortable;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;
import com.intellij.psi.search.scope.packageSet.PackageSet;
import com.intellij.ui.ComponentUtil;
import com.intellij.util.EventDispatcher;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashingStrategy;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.JBUI;
import org.jdom.Attribute;
import org.jdom.Element;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.intellij.openapi.options.newEditor.ConfigurablesListPanelKt.createConfigurablesListPanel;
import static com.intellij.ui.ScrollPaneFactory.createScrollPane;
import static javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER;

public class ColorAndFontOptions extends SearchableConfigurable.Parent.Abstract
  implements EditorOptionsProvider, SchemesModel<EditorColorsScheme>, Configurable.WithEpDependencies, Configurable.NoScroll, Configurable.NoMargin {
  private static final Logger LOG = Logger.getInstance(ColorAndFontOptions.class);

  public static final String ID = "reference.settingsdialog.IDE.editor.colors";

  private final ColorAndFontOptionsModel myModel = ColorAndFontOptionsModel.getInstance();
  ColorAndFontOptionsModelListener modelListener = new ColorAndFontOptionsModelListener() {
    @Override
    public void onChanged() {
      if (myInitResetCompleted) resetSchemesCombo(myModel);
    }
  };

  private boolean mySomeSchemesDeleted = false;
  private Map<ColorAndFontPanelFactory, InnerSearchableConfigurable> mySubPanelFactories;

  private SchemesPanelFactory mySchemesPanelFactory;
  private SchemesPanel myRootSchemesPanel;

  private boolean myInitResetCompleted = false;
  private boolean myInitResetInvoked = false;

  private boolean myRevertChangesCompleted = false;

  private boolean myApplyCompleted = false;
  private boolean myDisposeCompleted = false;
  private final Disposable myDisposable = Disposer.newDisposable();

  private final EventDispatcher<ColorAndFontSettingsListener> myDispatcher = EventDispatcher.create(ColorAndFontSettingsListener.class);

  private MessageBusConnection myEditorColorSchemeConnection;
  private boolean myShouldChangeLafIfNecessary = true;

  public ColorAndFontOptions() {
    myModel.addListener(modelListener);
  }

  public void addListener(@NotNull ColorAndFontSettingsListener listener) {
    myDispatcher.addListener(listener);
  }

  public void stateChanged() {
    myDispatcher.getMulticaster().settingsChanged();
  }

  @Override
  public String getDisplayName() {
    return ApplicationBundle.message("title.colors.and.fonts");
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

  private Iterable<MyColorScheme> getMySchemes() {
    return myModel.allSchemes().stream().filter((scheme) -> {
      return scheme instanceof MyColorScheme;
    }).map((scheme -> {
      return (MyColorScheme) scheme;
    })).toList();
  }

  private @Nullable MyColorScheme getMyScheme(@NotNull String name) {
    Scheme scheme = myModel.getScheme(name);
    if (scheme instanceof MyColorScheme) return (MyColorScheme)scheme;
    else return null;
  }

  private MyColorScheme getMySelectedScheme() {
    Scheme scheme = myModel.getSelectedScheme();
    if (scheme instanceof MyColorScheme) return (MyColorScheme)scheme;
    else return null;
  }

  private boolean isSchemeListModified() {
    if (mySomeSchemesDeleted) return true;

    EditorColorsScheme selectedScheme = myModel.getSelectedScheme();
    if (selectedScheme == null ||
        !selectedScheme.getName().equals(EditorColorsManager.getInstance().getGlobalScheme().getName())) return true;

    for (MyColorScheme scheme : getMySchemes()) {
      if (scheme.isNew()) return true;
    }

    return false;
  }

  private boolean isSomeSchemeModified() {
    for (MyColorScheme scheme : getMySchemes()) {
      if (scheme.isModified()) return true;
    }
    return false;
  }

  public EditorColorsScheme selectScheme(@NotNull String name) {
    MyColorScheme schemeToSelect = getMyScheme(name);
    if (schemeToSelect != null) {
      myModel.setSelectedScheme(schemeToSelect, this);
    }
    else {
      LOG.warn("Scheme " + name + " can not be selected. Schemes: " + myModel.schemesKeySet());
    }

    return myModel.getSelectedScheme();
  }

  public EditorColorsScheme getSelectedScheme() {
    return myModel.getSelectedScheme();
  }

  public EditorSchemeAttributeDescriptor[] getCurrentDescriptions() {
    MyColorScheme selectedScheme = getMySelectedScheme();
    if (selectedScheme != null) return selectedScheme.getDescriptors();
    return new EditorSchemeAttributeDescriptor[0];
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
      scheme.getName().startsWith(Scheme.EDITABLE_COPY_PREFIX) &&
      (originalScheme != null && originalScheme.isReadOnly());
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
    return myModel.getScheme(name) != null || myModel.getScheme(Scheme.EDITABLE_COPY_PREFIX + name) != null;
  }

  @Override
  public boolean differsFromDefault(@NotNull EditorColorsScheme scheme) {
    if (scheme.getName().startsWith(Scheme.EDITABLE_COPY_PREFIX)) {
      String displayName = Scheme.getBaseName(scheme.getName());
      EditorColorsScheme defaultScheme = DefaultColorSchemesManager.getInstance().getScheme(displayName);
      if (defaultScheme == null) {
        defaultScheme = EditorColorsManager.getInstance().getScheme(displayName);
      }
      if (defaultScheme != null && scheme instanceof AbstractColorsScheme) {
        return !((AbstractColorsScheme)scheme)
          .settingsEqual(defaultScheme, colorKey -> !colorKey.getExternalName().startsWith(FileStatusFactory.getFilestatusColorKeyPrefix()));
      }
    }
    return false;
  }

  @Override
  public boolean isDefaultScheme(@NotNull EditorColorsScheme scheme) {
    return UIThemeLookAndFeelInfoKt.isDefaultForTheme(scheme, LafManager.getInstance().getCurrentUIThemeLookAndFeel());
  }

  @Override
  public @NotNull Collection<BaseExtensionPointName<?>> getDependencies() {
    return List.of(ColorSettingsPage.EP_NAME, ColorAndFontPanelFactory.EP_NAME, ColorAndFontDescriptorsProvider.EP_NAME);
  }

  @ApiStatus.Internal
  public void setSchemesPanelFactory(SchemesPanelFactory schemesPanelFactory) {
    mySchemesPanelFactory = schemesPanelFactory;
  }

  @ApiStatus.Internal
  public void setShouldChangeLafIfNecessary(boolean shouldChangeLafIfNecessary) {
    myShouldChangeLafIfNecessary = shouldChangeLafIfNecessary;
  }

  public static boolean isReadOnly(final @NotNull EditorColorsScheme scheme) {
    return scheme.isReadOnly();
  }

  public static boolean canBeDeleted(final @NotNull EditorColorsScheme scheme) {
    return scheme instanceof  MyColorScheme && ((MyColorScheme)scheme).canBeDeleted();
  }

  public @NotNull Groups<EditorColorsScheme> getOrderedSchemes() {
    return myModel.getOrderedSchemes();
  }

  public @NotNull Collection<EditorColorsScheme> getSchemes() {
    return new ArrayList<>(myModel.allSchemes());
  }

  public boolean saveSchemeAs(@NotNull EditorColorsScheme editorScheme, @NotNull String name) {
    if (editorScheme instanceof MyColorScheme scheme) {
      EditorColorsScheme clone = (EditorColorsScheme)scheme.getParentScheme().clone();
      scheme.apply(clone);
      if (clone instanceof AbstractColorsScheme) {
        ((AbstractColorsScheme)clone).setSaveNeeded(true);
      }

      clone.setName(name);
      MyColorScheme newScheme = new MyColorScheme(clone);
      initScheme(newScheme);

      newScheme.setIsNew();

      myModel.runBatchedUpdate(this, () -> {
        myModel.putScheme(name, newScheme, this);
        selectScheme(newScheme.getName());
      });
      resetSchemesCombo(null);
      return true;
    }
    return false;
  }

  public void addImportedScheme(@NotNull EditorColorsScheme imported) {
    if (imported instanceof AbstractColorsScheme) ((AbstractColorsScheme)imported).setSaveNeeded(true);
    MyColorScheme newScheme = new MyColorScheme(imported);
    initScheme(newScheme);

    myModel.runBatchedUpdate(this, () -> {
      myModel.putScheme(imported.getName(), newScheme, this);
      selectScheme(newScheme.getName());
    });
    resetSchemesCombo(null);
  }

  @Override
  public void removeScheme(@NotNull EditorColorsScheme scheme) {
    String schemeName = scheme.getName();
    EditorColorsScheme selectedScheme = getSelectedScheme();
    if (selectedScheme != null && selectedScheme.getName().equals(schemeName)) {
      selectDefaultScheme();
    }

    boolean deletedNewlyCreated = false;

    MyColorScheme toDelete = getMyScheme(schemeName);

    if (toDelete != null) {
      deletedNewlyCreated = toDelete.isNew();
    }

    myModel.removeScheme(schemeName, this);
    resetSchemesCombo(null);
    mySomeSchemesDeleted = mySomeSchemesDeleted || !deletedNewlyCreated;
  }

  private void selectDefaultScheme() {
    DefaultColorsScheme defaultScheme =
      (DefaultColorsScheme)EditorColorsManager.getInstance().getDefaultScheme();
    selectScheme(defaultScheme.getEditableCopyName());
  }


  void resetSchemeToOriginal(@NotNull String name) {
    MyColorScheme schemeToReset = getMyScheme(name);
    if (schemeToReset != null) schemeToReset.resetToOriginal();
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
      EditorColorsManager editorColorManager = EditorColorsManager.getInstance();
      SchemeManager<EditorColorsScheme> schemeManager = ((EditorColorsManagerImpl)editorColorManager).getSchemeManager();
      EditorColorsScheme oldScheme = editorColorManager.getGlobalScheme();

      List<EditorColorsScheme> result = new ArrayList<>(myModel.allSchemes().size());
      boolean activeSchemeModified = false;
      MyColorScheme selectedScheme = getMySelectedScheme();
      if (selectedScheme == null) return;

      EditorColorsScheme activeOriginalScheme = selectedScheme.getParentScheme();
      for (MyColorScheme scheme : getMySchemes()) {
        boolean isModified = scheme.apply();
        if (isModified && !activeSchemeModified && activeOriginalScheme == scheme.getParentScheme()) {
          activeSchemeModified = true;
        }
        result.add(scheme.getParentScheme());
      }

      // refresh only if the scheme is not switched
      boolean refreshEditors = activeSchemeModified && schemeManager.getActiveScheme() == activeOriginalScheme;
      schemeManager.setSchemes(includingInvisible(result, schemeManager), activeOriginalScheme);
      if (refreshEditors) {
        ((EditorColorsManagerImpl)EditorColorsManager.getInstance()).schemeChangedOrSwitched(null);
      }

      if (myShouldChangeLafIfNecessary && !Objects.equals(oldScheme.getName(), editorColorManager.getGlobalScheme().getName())) {
        QuickChangeColorSchemeAction.changeLafIfNecessary(oldScheme, activeOriginalScheme, null);
      }

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

  private boolean myIsReset = false;

  @ApiStatus.Internal
  public void preselectScheme(@NotNull String schemeName) {
    myModel.runBatchedUpdate(this, () -> {
      if (myModel.getScheme(schemeName) != null) {
        selectScheme(schemeName);

        if (myInitResetCompleted) {
          resetSchemesCombo(this);
        }
      }

      if (!myInitResetCompleted) {
        myModel.setPreselectedSchemeName(schemeName, this);
      }
    });
  }

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
    return createComponent(false);
  }

  @ApiStatus.Internal
  public JComponent createComponent(boolean comboBoxOnly) {
    if (myRootSchemesPanel == null) {
      ensureSchemesPanel();
    }

    if (comboBoxOnly) {
      return myRootSchemesPanel;
    }
    else {
      JPanel container = new JPanel();
      container.setLayout(new BorderLayout());
      container.setBorder(JBUI.Borders.empty(11, 16, 0, 16));

      container.add(BorderLayout.NORTH, myRootSchemesPanel);
      container.add(BorderLayout.CENTER, createChildSectionLinkList());

      return container;
    }
  }

  private JComponent createChildSectionLinkList() {
    JComponent content = new JPanel(new BorderLayout());
    content.setBorder(JBUI.Borders.emptyTop(11));
    content.add(BorderLayout.CENTER, createConfigurablesListPanel(ApplicationBundle.message("description.colors.and.fonts"),
                                                                  Arrays.asList(getConfigurables()),
                                                                  null));

    JScrollPane pane = createScrollPane(content, true);
    pane.setHorizontalScrollBarPolicy(HORIZONTAL_SCROLLBAR_NEVER);
    return pane;
  }

  @Override
  public boolean hasOwnContent() {
    return true;
  }

  @Override
  public @NotNull Configurable @NotNull [] buildConfigurables() {
    myDisposeCompleted = false;
    initAll();

    List<ColorAndFontPanelFactory> panelFactories = createPanelFactories();

    mySubPanelFactories = new LinkedHashMap<>(panelFactories.size());
    for (ColorAndFontPanelFactory panelFactory : panelFactories) {
      mySubPanelFactories.put(panelFactory, new InnerSearchableConfigurable(panelFactory));
    }

    return mySubPanelFactories.values().toArray(new Configurable[0]);
  }

  private @NotNull Set<NewColorAndFontPanel> getPanels() {
    if (mySubPanelFactories == null) return new HashSet<>();

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
    List<ColorAndFontPanelFactory> extensions = new ArrayList<>();
    extensions.add(new FontConfigurableFactory());
    extensions.add(new ConsoleFontConfigurableFactory());
    ColorSettingsPage[] pages = ColorSettingsPages.getInstance().getRegisteredPages();
    for (final ColorSettingsPage page : pages) {
      extensions.add(new ColorAndFontPanelFactoryEx() {
        @Override
        public @NotNull NewColorAndFontPanel createPanel(@NotNull ColorAndFontOptions options) {
          final SimpleEditorPreview preview = new SimpleEditorPreview(options, page);
          return NewColorAndFontPanel.create(preview, page.getDisplayName(), options, null, page);
        }

        @Override
        public @NotNull String getPanelDisplayName() {
          return page.getDisplayName();
        }

        @Override
        public DisplayPriority getPriority() {
          if (page instanceof DisplayPrioritySortable) {
            return ((DisplayPrioritySortable)page).getPriority();
          }
          return DisplayPriority.LANGUAGE_SETTINGS;
        }

        @Override
        public int getWeight() {
          if (page instanceof DisplayPrioritySortable) {
            return ((DisplayPrioritySortable)page).getWeight();
          }
          return ColorAndFontPanelFactoryEx.super.getWeight();
        }

        @Override
        public @NotNull Class<?> getOriginalClass() {
          return page.getClass();
        }
      });
    }
    extensions.addAll(ColorAndFontPanelFactory.EP_NAME.getExtensionList());
    extensions.sort(
      (f1, f2) -> DisplayPrioritySortable.compare(f1, f2, factory -> factory.getPanelDisplayName())
    );
    return new ArrayList<>(extensions);
  }

  public static @NlsContexts.ConfigurableName String getFontConfigurableName() {
    return ApplicationBundle.message("title.colors.scheme.font");
  }

  private static final class FontConfigurableFactory implements ColorAndFontPanelFactoryEx {
    @Override
    public @NotNull NewColorAndFontPanel createPanel(@NotNull ColorAndFontOptions options) {
      FontEditorPreview previewPanel = new FontEditorPreview(()->options.getSelectedScheme(), true);
      return new NewColorAndFontPanel(new SchemesPanel(options, 0), new FontOptions(options), previewPanel, getFontConfigurableName(), null, null){
        @Override
        public boolean containsFontOptions() {
          return true;
        }
      };
    }

    @Override
    public @NotNull String getPanelDisplayName() {
      return getFontConfigurableName();
    }

    @Override
    public DisplayPriority getPriority() {
      return DisplayPriority.FONT_SETTINGS;
    }
  }

   private static final class ConsoleFontConfigurableFactory implements ColorAndFontPanelFactoryEx {
    @Override
    public @NotNull NewColorAndFontPanel createPanel(@NotNull ColorAndFontOptions options) {
      FontEditorPreview previewPanel = new FontEditorPreview(()->options.getSelectedScheme(), true) {
        @Override
        protected EditorColorsScheme updateOptionsScheme(EditorColorsScheme selectedScheme) {
          return ConsoleViewUtil.updateConsoleColorScheme(selectedScheme);
        }
      };
      return new NewColorAndFontPanel(new SchemesPanel(options, 0), new ConsoleFontOptions(options), previewPanel, ApplicationBundle.message("label.font.type"), null, null){
        @Override
        public boolean containsFontOptions() {
          return true;
        }
      };
    }

    @Override
    public @NotNull String getPanelDisplayName() {
      return ApplicationBundle.message("title.console.font");
    }

     @Override
     public @NotNull DisplayPriority getPriority() {
       return DisplayPriority.FONT_SETTINGS;
     }
   }

  private void initAll() {
    EditorColorsScheme globalScheme = EditorColorsManager.getInstance().getGlobalScheme();

    myModel.runBatchedUpdate(this, () -> {
      myModel.dropSchemes(this);
      for (EditorColorsScheme allScheme : EditorColorsManager.getInstance().getAllSchemes()) {
        MyColorScheme schemeDelegate = new MyColorScheme(allScheme);
        initScheme(schemeDelegate);
        myModel.putScheme(schemeDelegate.getName(), schemeDelegate, this);
      }

      EditorColorsScheme schemeToSelect = null;
      String preselectedSchemeName = myModel.getPreselectedSchemeName();
      if (preselectedSchemeName != null) {
        schemeToSelect = myModel.getScheme(preselectedSchemeName);
        myModel.setPreselectedSchemeName(null, this);
      }

      if (schemeToSelect == null) {
        schemeToSelect = globalScheme;

        if (EditorColorsManagerImpl.Companion.isTempScheme(schemeToSelect)) {
          MyColorScheme schemeDelegate = new MyTempColorScheme((AbstractColorsScheme)schemeToSelect);
          initScheme(schemeDelegate);
          myModel.putScheme(schemeDelegate.getName(), schemeDelegate, this);
        }
      }

      myModel.setSelectedScheme(myModel.getScheme(schemeToSelect.getName()), this);
      assert myModel.getSelectedScheme() != null :
        schemeToSelect.getName() + "; mySchemes=" +
        ContainerUtil.map(myModel.schemesKeySet(), key -> "(" + key + ": " + myModel.getScheme(key) + ")");
    });
  }

  private static void initScheme(@NotNull MyColorScheme scheme) {
    List<EditorSchemeAttributeDescriptor> descriptions = new ArrayList<>();
    initPluggedDescriptions(descriptions, scheme);
    initScopesDescriptors(descriptions, scheme);

    scheme.setDescriptors(descriptions.toArray(new EditorSchemeAttributeDescriptor[0]));
  }

  private static void initPluggedDescriptions(@NotNull List<? super EditorSchemeAttributeDescriptor> descriptions,
                                              @NotNull MyColorScheme scheme) {
    ColorSettingsPage[] pages = ColorSettingsPages.getInstance().getRegisteredPages();
    for (ColorSettingsPage page : pages) {
      try {
        initDescriptions(page, descriptions, scheme);
      }
      catch (ProcessCanceledException e) {
        throw e;
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }
    for (ColorAndFontDescriptorsProvider provider : ColorAndFontDescriptorsProvider.EP_NAME.getExtensionList()) {
      try {
        initDescriptions(provider, descriptions, scheme);
      }
      catch (ProcessCanceledException e) {
        throw e;
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }
  }

  private static void initDescriptions(@NotNull ColorAndFontDescriptorsProvider provider,
                                       @NotNull List<? super EditorSchemeAttributeDescriptor> descriptions,
                                       @NotNull MyColorScheme scheme) {
    String className = provider.getClass().getName();
    String group = provider.getDisplayName();
    List<AttributesDescriptor> attributeDescriptors = ColorSettingsUtil.getAllAttributeDescriptors(provider);
    if (provider instanceof RainbowColorSettingsPage) {
      RainbowAttributeDescriptor d = new RainbowAttributeDescriptor(
        ((RainbowColorSettingsPage)provider).getLanguage(), group,
        ApplicationBundle.message("rainbow.option.panel.display.name"),
        scheme, scheme.myRainbowState);
      descriptions.add(d);
    }
    for (AttributesDescriptor descriptor : attributeDescriptors) {
      if (descriptor == null) {
        LOG.warn("Null attribute descriptor in " + className);
        continue;
      }
      SchemeTextAttributesDescription d = new SchemeTextAttributesDescription(
        descriptor.getDisplayName(), group, descriptor.getKey(), scheme, null, null);
      descriptions.add(d);
    }
    for (ColorDescriptor descriptor : provider.getColorDescriptors()) {
      if (descriptor == null) {
        LOG.warn("Null color descriptor in " + className);
        continue;
      }
      EditorSettingColorDescription d = new EditorSettingColorDescription(
        descriptor.getDisplayName(), group, descriptor.getKey(), descriptor.getKind(), scheme);
      descriptions.add(d);
    }
  }


  private static void initScopesDescriptors(@NotNull List<? super EditorSchemeAttributeDescriptor> descriptions, @NotNull MyColorScheme scheme) {
    Set<Pair<NamedScope,NamedScopesHolder>> namedScopes = CollectionFactory.createCustomHashingStrategySet(new HashingStrategy<>() {
      @Override
      public int hashCode(@Nullable Pair<NamedScope, NamedScopesHolder> object) {
        return object == null ? 0 : object.getFirst().getScopeId().hashCode();
      }

      @Override
      public boolean equals(@Nullable Pair<NamedScope, NamedScopesHolder> o1, @Nullable Pair<NamedScope, NamedScopesHolder> o2) {
        return o1 == o2 || (o1 != null && o2 != null && o1.getFirst().getScopeId().equals(o2.getFirst().getScopeId()));
      }
    });
    Project[] projects = ProjectManager.getInstance().getOpenProjects();
    for (Project project : projects) {
      DependencyValidationManagerImpl validationManager = (DependencyValidationManagerImpl)DependencyValidationManager.getInstance(project);
      List<Pair<NamedScope,NamedScopesHolder>> cachedScopes = validationManager.getScopeBasedHighlightingCachedScopes();
      namedScopes.addAll(cachedScopes);
    }

    List<Pair<NamedScope, NamedScopesHolder>> list = new ArrayList<>(namedScopes);

    list.sort((o1, o2) -> o1.getFirst().getPresentableName().compareToIgnoreCase(o2.getFirst().getPresentableName()));
    for (Pair<NamedScope,NamedScopesHolder> pair : list) {
      NamedScope namedScope = pair.getFirst();
      String name = namedScope.getScopeId();
      TextAttributesKey textAttributesKey = ScopeAttributesUtil.getScopeTextAttributeKey(name);
      if (scheme.getAttributes(textAttributesKey) == null) {
        scheme.setAttributes(textAttributesKey, new TextAttributes());
      }
      NamedScopesHolder holder = pair.getSecond();

      PackageSet value = namedScope.getValue();
      String toolTip = holder.getDisplayName() + (value==null ? "" : ": "+ value.getText());
      descriptions.add(new SchemeTextAttributesDescription(namedScope.getPresentableName(), getScopesGroup(), textAttributesKey, scheme, holder.getIcon(), toolTip));
    }
  }

  private void editorColorSchemeChanged(@Nullable EditorColorsScheme scheme) {
    if (myModel.getSelectedScheme() == null || scheme == null) return;
    if (myModel.getSelectedScheme().getName().equals(scheme.getName()) ||
        Scheme.getBaseName(myModel.getSelectedScheme().getName()).equals(scheme.getName())) return;

    reset();
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
    updateEnabledState();
  }

  boolean isSchemesPanelEnabled() {
    return !LafManager.getInstance().getAutodetect();
  }

  private void updateEnabledState() {
    if (myRootSchemesPanel != null) {
      boolean isEnabled = isSchemesPanelEnabled();
      myRootSchemesPanel.setEnabled(isEnabled);
      for (NewColorAndFontPanel subPanel: getPanels()) {
        subPanel.setSchemesPanelEnabled(isEnabled);
      }
    }
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
      if (mySchemesPanelFactory == null) {
        myRootSchemesPanel = new SchemesPanel(this, 0);
      }
      else {
        myRootSchemesPanel = mySchemesPanelFactory.createSchemesPanel(this);
      }

      myRootSchemesPanel.addListener(new ColorAndFontSettingsListener.Abstract(){
        @Override
        public void schemeChanged(final @NotNull Object source) {
          if (!myIsReset) {
            resetSchemesCombo(source);
          }
        }
      });

      ensureSynchronizingWithGlobalScheme();
    }
  }

  private void ensureSynchronizingWithGlobalScheme() {
    if (myEditorColorSchemeConnection == null) {
      myEditorColorSchemeConnection = ApplicationManager.getApplication().getMessageBus().connect(myDisposable);
      myEditorColorSchemeConnection.subscribe(EditorColorsManager.TOPIC, scheme -> editorColorSchemeChanged(scheme));
      myEditorColorSchemeConnection.subscribe(LafManagerListener.TOPIC, source -> updateEnabledState());
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

      myModel.removeListener(modelListener);
    }
  }

  private static final class SchemeTextAttributesDescription extends TextAttributesDescription implements UiInspectorContextProvider {
    private final @NotNull TextAttributes myInitialAttributes;
    private final @NotNull TextAttributesKey key;

    private TextAttributes myFallbackAttributes;
    private Pair<ColorAndFontDescriptorsProvider, AttributesDescriptor> myBaseAttributeDescriptor;
    private final boolean myIsInheritedInitial;

    private SchemeTextAttributesDescription(String name,
                                            String group,
                                            @NotNull TextAttributesKey key,
                                            @NotNull MyColorScheme scheme,
                                            Icon icon,
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
      if (myIsInheritedInitial && myFallbackAttributes != null) {
        getTextAttributes().copyFrom(myFallbackAttributes);
      }
      initCheckedStatus();
    }

    @Override
    public @NotNull List<PropertyBean> getUiInspectorContext() {
      List<PropertyBean> result = new ArrayList<>();
      result.add(new PropertyBean("Text Attributes Key", key.getExternalName(), true));
      return result;
    }

    private static @NotNull TextAttributes getInitialAttributes(@NotNull MyColorScheme scheme, @NotNull TextAttributesKey key) {
      TextAttributes attributes = scheme.getAttributes(key);
      return attributes != null ? attributes : new TextAttributes();
    }

    @Override
    public void apply(EditorColorsScheme scheme) {
      if (scheme == null) scheme = getScheme();
      if (scheme == null) return;
      boolean skip = scheme instanceof EditorColorsSchemeImpl && isInherited() && myIsInheritedInitial;
      if (!skip) {
        // IDEA-162844 set only if previously was not inherited (and, so, we must mark it as inherited)
        scheme.setAttributes(key, isInherited() ? AbstractColorsScheme.INHERITED_ATTRS_MARKER : getTextAttributes());
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

    @Override
    public @Nullable TextAttributes getBaseAttributes() {
      return myFallbackAttributes;
    }

    @Override
    public @Nullable Pair<ColorAndFontDescriptorsProvider, ? extends AbstractKeyDescriptor> getFallbackKeyDescriptor() {
      return myBaseAttributeDescriptor;
    }
    @Override
    public boolean isTransparencyEnabled() { return true; }
  }

  private static final class EditorSettingColorDescription extends ColorAndFontDescription implements UiInspectorContextProvider {
    private final ColorKey myColorKey;
    private final @NotNull ColorDescriptor.Kind myKind;
    private final Color myInitialColor;

    private Color myColor;
    private TextAttributes myFallbackAttributes;
    private Pair<ColorAndFontDescriptorsProvider, ColorDescriptor> myBaseAttributeDescriptor;
    private final boolean myIsInheritedInitial;

    EditorSettingColorDescription(String name,
                                  String group,
                                  @NotNull ColorKey colorKey,
                                  @NotNull ColorDescriptor.Kind kind,
                                  @NotNull MyColorScheme scheme) {
      super(name, group, colorKey.getExternalName(), scheme, null, null);
      myColorKey = colorKey;
      myKind = kind;
      ColorKey fallbackKey = myColorKey.getFallbackColorKey();
      if (fallbackKey != null) {
        myBaseAttributeDescriptor = ColorSettingsPages.getInstance().getColorDescriptor(fallbackKey);
        if (myBaseAttributeDescriptor == null) {
          @NlsSafe String fallbackKeyExternalName = fallbackKey.getExternalName();
          myBaseAttributeDescriptor = Pair.create(null, new ColorDescriptor(fallbackKeyExternalName, fallbackKey, myKind));
        }
        Color fallbackColor = scheme.getColor(fallbackKey);
        myFallbackAttributes = new TextAttributes(myKind == ColorDescriptor.Kind.FOREGROUND ? fallbackColor : null,
                                                  myKind == ColorDescriptor.Kind.BACKGROUND ? fallbackColor : null,
                                                  null, null, Font.PLAIN);
      }
      myColor = scheme.getColor(myColorKey);
      myInitialColor = myColor;

      myIsInheritedInitial = scheme.isInherited(myColorKey);
      setInherited(myIsInheritedInitial);
      if (myIsInheritedInitial) {
        //setInheritedAttributes(getTextAttributes());
      }
      initCheckedStatus();
    }

    @Override
    public @NotNull List<PropertyBean> getUiInspectorContext() {
      List<PropertyBean> result = new ArrayList<>();
      result.add(new PropertyBean("Color Key", myColorKey.getExternalName(), true));
      return result;
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

    @Override
    public @NotNull EffectType getExternalEffectType() {
      return EffectType.LINE_UNDERSCORE;
    }

    @Override
    public Color getExternalForeground() {
      return isForegroundEnabled() ? myColor : null;
    }

    @Override
    public void setExternalForeground(Color col) {
      if (!myKind.isForeground()) return;
      if (myColor != null && myColor.equals(col)) return;
      myColor = col;
    }

    @Override
    public Color getExternalBackground() {
      return isBackgroundEnabled() ? myColor : null;
    }

    @Override
    public void setExternalBackground(Color col) {
      if (!myKind.isBackground()) return;
      if (myColor != null && myColor.equals(col)) return;
      myColor = col;
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
      return myKind.isForeground();
    }

    @Override
    public boolean isBackgroundEnabled() {
      return myKind.isBackground();
    }

    @Override
    public boolean isTransparencyEnabled() {
      return myKind.isWithTransparency();
    }

    @Override
    public boolean isEffectsColorEnabled() {
      return false;
    }

    @Override
    public boolean isModified() {
      if (isInherited()) {
        return !myIsInheritedInitial;
      }
      return !Comparing.equal(myInitialColor, myColor) || myIsInheritedInitial;
    }

    @Override
    public @Nullable TextAttributes getBaseAttributes() {
      return myFallbackAttributes;
    }

    @Override
    public void apply(EditorColorsScheme scheme) {
      if (scheme == null) scheme = getScheme();
      if (scheme == null) return;
      boolean skip = scheme instanceof EditorColorsSchemeImpl && isInherited() && myIsInheritedInitial;
      if (!skip) {
        scheme.setColor(myColorKey, isInherited() ? AbstractColorsScheme.INHERITED_COLOR_MARKER : myColor);
      }
    }

    @Override
    public @Nullable Pair<ColorAndFontDescriptorsProvider, ? extends AbstractKeyDescriptor> getFallbackKeyDescriptor() {
      return myBaseAttributeDescriptor;
    }
  }

  @Override
  public @NotNull String getHelpTopic() {
    return ID;
  }

  private static class MyColorScheme extends EditorColorsSchemeImpl {
    private EditorSchemeAttributeDescriptor[] myDescriptors;
    private String                            myName;
    private boolean myIsNew = false;
    private final RainbowColorsInSchemeState myRainbowState;
    private static final Predicate<ColorKey> FILE_STATUS_COLORS =
      input -> input != null && input.getExternalName().startsWith(FileStatusFactory.getFilestatusColorKeyPrefix());


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

      myName = parentScheme.getName();

      RainbowHighlighter.transferRainbowState(this, parentScheme);
      myRainbowState = new RainbowColorsInSchemeState(this, parentScheme);

      initFonts();
    }

    @Override
    public @Nullable AbstractColorsScheme getOriginal() {
      return parentScheme instanceof AbstractColorsScheme ? ((AbstractColorsScheme)parentScheme).getOriginal() : null;
    }

    @Override
    public @NotNull String getName() {
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
      return parentScheme.isReadOnly();
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
      return (parentScheme instanceof AbstractColorsScheme) && ((AbstractColorsScheme)parentScheme).canBeDeleted();
    }

    private boolean isFontModified() {
      return !areDelegatingOrEqual(getFontPreferences(), parentScheme.getFontPreferences());
    }

    private boolean isConsoleFontModified() {
      return !areDelegatingOrEqual(getConsoleFontPreferences(), parentScheme.getConsoleFontPreferences());
    }

    protected boolean apply() {
      return !parentScheme.isReadOnly() && apply(parentScheme);
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
      throw new UnsupportedOperationException();
    }

    public void setIsNew() {
      myIsNew = true;
    }

    public boolean isNew() {
      return myIsNew;
    }

    @Override
    public @NotNull String toString() {
      return "temporary scheme for " + myName;
    }

    public boolean isInherited(@NotNull TextAttributesKey key) {
      TextAttributesKey fallbackKey = key.getFallbackAttributeKey();
      if (fallbackKey != null) {
        if (parentScheme instanceof AbstractColorsScheme) {
          TextAttributes ownAttrs = ((AbstractColorsScheme)parentScheme).getDirectlyDefinedAttributes(key);
          if (ownAttrs != null) {
            return ownAttrs == INHERITED_ATTRS_MARKER;
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

    public boolean isInherited(ColorKey key) {
      ColorKey fallbackKey = key.getFallbackColorKey();
      if (fallbackKey != null) {
        if (parentScheme instanceof AbstractColorsScheme) {
          Color ownAttrs = ((AbstractColorsScheme)parentScheme).getDirectlyDefinedColor(key);
          if (ownAttrs != null) {
            return ownAttrs == INHERITED_COLOR_MARKER;
          }
        }
        Color attributes = getColor(key);
        if (attributes != null) {
          Color fallback = getColor(fallbackKey);
          return attributes == fallback;
        }
      }
      return false;
    }

    public void resetToOriginal() {
      if (parentScheme instanceof AbstractColorsScheme) {
        AbstractColorsScheme originalScheme = ((AbstractColorsScheme)parentScheme).getOriginal();
        if (originalScheme != null) {
          copyPreservingFileStatusColors(originalScheme, (AbstractColorsScheme)parentScheme);
          copyPreservingFileStatusColors(originalScheme, this);
          initScheme(this);
        }
      }
    }

    private static void copyPreservingFileStatusColors(@NotNull AbstractColorsScheme source,
                                                       @NotNull AbstractColorsScheme target) {
      Map<ColorKey, Color> fileStatusColors = target.getColorKeys().stream().filter(FILE_STATUS_COLORS).collect(
        Collectors.toMap(Function.identity(), target::getDirectlyDefinedColor));
      source.copyTo(target);
      for (ColorKey key : fileStatusColors.keySet()) {
        target.setColor(key, fileStatusColors.get(key));
      }
      target.setSaveNeeded(true);
    }
  }

  private static final class MyTempColorScheme extends MyColorScheme {
    private MyTempColorScheme(@NotNull AbstractColorsScheme parentScheme) {
      super(parentScheme);
    }

    @Override
    public boolean isReadOnly() {
      return false;
    }

    @Override
    protected boolean apply() {
      Element scheme = new Element("scheme");
      EditorColorsScheme parentScheme = getParentScheme();
      ((AbstractColorsScheme)parentScheme).writeExternal(scheme);
      Element changes = new Element("scheme");
      writeExternal(changes);
      deepMerge(scheme, changes);
      writeTempScheme(scheme, parentScheme);
      return true;
    }

    private static void deepMerge(Element to, Element from) {
      List<Element> children = from.getChildren();
      Map<Pair<String, String>, Element> index = createNamedIndex(to);
      if (children.isEmpty()) {
        Pair<String, String> key = indexKey(from);
        Element el = index.get(key);
        org.jdom.Parent parent = to.getParent();
        if (el == null && parent != null) {
          if (!"".equals(from.getAttributeValue("value"))) {
            parent.addContent(from.clone());
          }
          parent.removeContent(to);
        }
      } else {
        for (Element child : children) {
          Element el = index.get(indexKey(child));
          if (el != null) {
            deepMerge(el, child);
          } else {
            to.addContent(child.clone());
          }
        }
      }
    }

    private static Map<Pair<String, String>, Element> createNamedIndex(Element e) {
      HashMap<Pair<String, String>, Element> index = new HashMap<>();
      for (Element child : e.getChildren()) {
        index.put(indexKey(child), child);
      }
      return index;
    }

    private static @NotNull Pair<String, String> indexKey(Element e) {
      return Pair.create(e.getName(), e.getAttributeValue("name"));
    }
  }

  public static void writeTempScheme(EditorColorsScheme colorsScheme) {
    Element scheme = new Element("scheme");
    ((AbstractColorsScheme)colorsScheme).writeExternal(scheme);
    writeTempScheme(scheme, colorsScheme);
  }

  public static void writeTempScheme(Element scheme, EditorColorsScheme parentScheme) {
    Path path = EditorColorsManagerImpl.Companion.getTempSchemeOriginalFilePath(parentScheme);
    if (path != null) {
      try {
        Element originalFile = JDOMUtil.load(path.toFile());
        scheme.setName(originalFile.getName());
        for (Attribute attribute : originalFile.getAttributes()) {
          scheme.setAttribute(attribute.getName(), attribute.getValue());
        }
        parentScheme.readExternal(scheme);

        scheme.removeChild("metaInfo");
        //save original metaInfo and don't add generated
        Element metaInfo = originalFile.getChild("metaInfo");
        if (metaInfo != null) {
          metaInfo = JDOMUtil.load(JDOMUtil.writeElement(metaInfo));
          scheme.addContent(0, metaInfo);
        }
        JDOMUtil.write(scheme, path);
        VirtualFileManager.getInstance().syncRefresh();
      }
      catch (Exception e) {
        LOG.warn(e);
      }
    }
  }

  @Override
  public @NotNull String getId() {
    return getHelpTopic();
  }

  public @Nullable SearchableConfigurable findSubConfigurable(@NotNull Class<?> pageClass) {
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

  public @Nullable SearchableConfigurable findSubConfigurable(String pageName) {
    if (mySubPanelFactories == null) {
      getConfigurables();
    }
    for (InnerSearchableConfigurable configurable : mySubPanelFactories.values()) {
      if (configurable.getDisplayName().equals(pageName)) {
        return configurable;
      }
    }
    return null;
  }

  public @Nullable NewColorAndFontPanel findPage(String pageName) {
    InnerSearchableConfigurable child = (InnerSearchableConfigurable)findSubConfigurable(pageName);
    return child == null ? null : child.createPanel();
  }

  private final class InnerSearchableConfigurable
    implements SearchableConfigurable, OptionsContainingConfigurable, NoScroll, InnerWithModifiableParent {
    private NewColorAndFontPanel mySubPanel;
    private boolean mySubInitInvoked = false;
    private final @NotNull ColorAndFontPanelFactory myFactory;

    private InnerSearchableConfigurable(@NotNull ColorAndFontPanelFactory factory) {
      myFactory = factory;
    }

    @Override
    public @NotNull @NlsContexts.ConfigurableName String getDisplayName() {
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
          public void schemeChanged(final @NotNull Object source) {
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
    public void focusOn(@Nls @NotNull String label) {
      createPanel().showOption(label);
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
      for (MyColorScheme scheme : getMySchemes()) {
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
    public @NotNull String getId() {
      return ColorAndFontOptions.this.getId() + "." + getDisplayName();
    }

    @Override
    public Runnable enableSearch(final String option) {
      return createPanel().showOption(option);
    }

    @Override
    public @NotNull Set<String> processListOptions() {
      return createPanel().processListOptions();
    }

    @Override
    public @NotNull Class<?> getOriginalClass() {
      return myFactory.getOriginalClass();
    }

    @Override
    public @NotNull @NonNls String toString() {
      return "Color And Fonts for "+getDisplayName();
    }

    @Override
    public @NotNull List<Configurable> getModifiableParents() {
      return List.of(ColorAndFontOptions.this);
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

  private static boolean selectOrEdit(DataContext context, String search, Function<? super ColorAndFontOptions, ? extends SearchableConfigurable> function) {
    return select(context, search, function) || edit(context, search, function);
  }

  private static boolean select(DataContext context, String search, Function<? super ColorAndFontOptions, ? extends SearchableConfigurable> function) {
    Settings settings = Settings.KEY.getData(context);
    if (settings == null) return false;

    ColorAndFontOptions options = settings.find(ColorAndFontOptions.class);
    if (options == null) return false;

    SearchableConfigurable page = function.apply(options);
    if (page == null) return false;

    settings.select(page, search);
    return true;
  }

  private static boolean edit(DataContext context, String search, Function<? super ColorAndFontOptions, ? extends SearchableConfigurable> function) {
    ColorAndFontOptions options = new ColorAndFontOptions();
    SearchableConfigurable page = function.apply(options);

    Configurable[] configurables = options.getConfigurables();
    try {
      if (page != null) {
        Runnable runnable = search == null ? null : page.enableSearch(search);
        Window window = ComponentUtil.getWindow(PlatformCoreDataKeys.CONTEXT_COMPONENT.getData(context));
        if (window != null) {
          ShowSettingsUtil.getInstance().editConfigurable(window, page, runnable);
        }
        else {
          ShowSettingsUtil.getInstance().editConfigurable(CommonDataKeys.PROJECT.getData(context), page, runnable);
        }
      }
    }
    finally {
      for (Configurable configurable : configurables) configurable.disposeUIResources();
      options.disposeUIResources();
    }
    return page != null;
  }

  public static @NlsContexts.ConfigurableName String getScopesGroup() {
    return ApplicationBundle.message("title.scope.based");
  }
}