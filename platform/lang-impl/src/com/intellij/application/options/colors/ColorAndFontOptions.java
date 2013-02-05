/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl;
import com.intellij.execution.impl.ConsoleViewUtil;
import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.laf.darcula.DarculaInstaller;
import com.intellij.ide.ui.laf.darcula.DarculaLaf;
import com.intellij.ide.ui.laf.darcula.DarculaLookAndFeelInfo;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.impl.settings.DiffOptionsPanel;
import com.intellij.openapi.diff.impl.settings.DiffPreviewPanel;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.colors.ex.DefaultColorSchemesManager;
import com.intellij.openapi.editor.colors.impl.DefaultColorsScheme;
import com.intellij.openapi.editor.colors.impl.EditorColorsSchemeImpl;
import com.intellij.openapi.editor.colors.impl.ReadOnlyColorsScheme;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.ExternalizableScheme;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.options.colors.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusFactory;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.psi.codeStyle.DisplayPriority;
import com.intellij.psi.codeStyle.DisplayPrioritySortable;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;
import com.intellij.psi.search.scope.packageSet.PackageSet;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.diff.FilesTooBigForDiffException;
import com.intellij.util.ui.UIUtil;
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

public class ColorAndFontOptions extends SearchableConfigurable.Parent.Abstract implements EditorOptionsProvider {
  private HashMap<String,MyColorScheme> mySchemes;
  private MyColorScheme mySelectedScheme;
  public static final String DIFF_GROUP = ApplicationBundle.message("title.diff");
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
  private static final Logger LOG = Logger.getInstance("#com.intellij.application.options.colors.ColorAndFontOptions");

  @Override
  public boolean isModified() {
    boolean listModified = isSchemeListModified();
    boolean schemeModified = isSomeSchemeModified();

    if (listModified || schemeModified) {
      myApplyCompleted = false;
    }

    return listModified;
  }

  private boolean isSchemeListModified(){
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

  private MyColorScheme getScheme(String name) {
    return mySchemes.get(name);
  }

  public EditorColorsScheme getSelectedScheme() {
    return mySelectedScheme;
  }

  public EditorColorsScheme getOriginalSelectedScheme() {
    return mySelectedScheme == null ? null : mySelectedScheme.getOriginalScheme();
  }

  public EditorSchemeAttributeDescriptor[] getCurrentDescriptions() {
    return mySelectedScheme.getDescriptors();
  }

  public static boolean isReadOnly(final EditorColorsScheme scheme) {
    return ((MyColorScheme)scheme).isReadOnly();
  }

  public String[] getSchemeNames() {
    ArrayList<MyColorScheme> schemes = new ArrayList<MyColorScheme>(mySchemes.values());
    Collections.sort(schemes, new Comparator<MyColorScheme>() {
      @Override
      public int compare(MyColorScheme o1, MyColorScheme o2) {
        if (isReadOnly(o1) && !isReadOnly(o2)) return -1;
        if (!isReadOnly(o1) && isReadOnly(o2)) return 1;

        return o1.getName().compareToIgnoreCase(o2.getName());
      }
    });

    ArrayList<String> names = new ArrayList<String>(schemes.size());
    for (MyColorScheme scheme : schemes) {
      names.add(scheme.getName());
    }

    return ArrayUtil.toStringArray(names);
  }

  public Collection<EditorColorsScheme> getSchemes() {
    return new ArrayList<EditorColorsScheme>(mySchemes.values());
  }

  public void saveSchemeAs(String name) {
    MyColorScheme scheme = mySelectedScheme;
    if (scheme == null) return;

    EditorColorsScheme clone = (EditorColorsScheme)scheme.getOriginalScheme().clone();

    scheme.apply(clone);

    clone.setName(name);
    MyColorScheme newScheme = new MyColorScheme(clone);
    initScheme(newScheme);

    newScheme.setIsNew();

    mySchemes.put(name, newScheme);
    selectScheme(newScheme.getName());
    resetSchemesCombo(null);
  }

  public void addImportedScheme(final EditorColorsScheme imported) {
    MyColorScheme newScheme = new MyColorScheme(imported);
    initScheme(newScheme);

    mySchemes.put(imported.getName(), newScheme);
    selectScheme(newScheme.getName());
    resetSchemesCombo(null);
  }

  public void removeScheme(String name) {
    if (mySelectedScheme.getName().equals(name)) {
      //noinspection HardCodedStringLiteral
      selectScheme("Default");
    }

    boolean deletedNewlyCreated = false;

    MyColorScheme toDelete = mySchemes.get(name);

    if (toDelete != null) {
      deletedNewlyCreated = toDelete.isNew();
    }

    mySchemes.remove(name);
    resetSchemesCombo(null);
    mySomeSchemesDeleted = mySomeSchemesDeleted || !deletedNewlyCreated;
  }

  @Override
  public void apply() throws ConfigurationException {
    if (!myApplyCompleted) {
      try {
        EditorColorsManager myColorsManager = EditorColorsManager.getInstance();

        myColorsManager.removeAllSchemes();
        for (MyColorScheme scheme : mySchemes.values()) {
          if (!scheme.isDefault()) {
            scheme.apply();
            myColorsManager.addColorsScheme(scheme.getOriginalScheme());
          }
        }

        EditorColorsScheme originalScheme = mySelectedScheme.getOriginalScheme();
        myColorsManager.setGlobalScheme(originalScheme);
        if (originalScheme != null && DarculaLaf.NAME.equals(originalScheme.getName()) && !UIUtil.isUnderDarcula()) {
          int ok = Messages.showYesNoDialog(
            "Darcula color scheme has been set for editors. Would you like to set Darcula as default Look and Feel?",
            "Darcula Look and Feel",
            Messages.getQuestionIcon());
          if (ok == Messages.OK) {
            LafManager.getInstance().setCurrentLookAndFeel(new DarculaLookAndFeelInfo());
            DarculaInstaller.install();
          }
        }
        applyChangesToEditors();

        reset();
      }
      finally {
        myApplyCompleted = true;


      }


    }


//    initAll();
//    resetSchemesCombo();


  }

  private static void applyChangesToEditors() {
    EditorFactory.getInstance().refreshAllEditors();

    Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
    for (Project openProject : openProjects) {
      FileStatusManager.getInstance(openProject).fileStatusesChanged();
      DaemonCodeAnalyzer.getInstance(openProject).restart();
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

  @Override
  public Configurable[] buildConfigurables() {
    myDisposeCompleted = false;
    initAll();

    List<ColorAndFontPanelFactory> panelFactories = createPanelFactories();

    List<Configurable> result = new ArrayList<Configurable>();
    mySubPanelFactories = new LinkedHashMap<ColorAndFontPanelFactory, InnerSearchableConfigurable>(panelFactories.size());
    for (ColorAndFontPanelFactory panelFactory : panelFactories) {
      mySubPanelFactories.put(panelFactory, new InnerSearchableConfigurable(panelFactory));
    }

    result.addAll(new ArrayList<SearchableConfigurable>(mySubPanelFactories.values()));
    return result.toArray(new Configurable[result.size()]);
  }

  private Set<NewColorAndFontPanel> getPanels() {
    Set<NewColorAndFontPanel> result = new HashSet<NewColorAndFontPanel>();
    for (InnerSearchableConfigurable configurable : mySubPanelFactories.values()) {
      NewColorAndFontPanel panel = configurable.getSubPanelIfInitialized();
      if (panel != null) {
        result.add(panel);
      }
    }
    return result;
  }

  protected List<ColorAndFontPanelFactory> createPanelFactories() {
    ArrayList<ColorAndFontPanelFactory> result = new ArrayList<ColorAndFontPanelFactory>();
    result.add(new FontConfigurableFactory());

    List<ColorAndFontPanelFactory> extensions = new ArrayList<ColorAndFontPanelFactory>();
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
    Collections.sort(extensions, new Comparator<ColorAndFontPanelFactory>() {
      @Override
      public int compare(ColorAndFontPanelFactory f1, ColorAndFontPanelFactory f2) {
        if (f1 instanceof DisplayPrioritySortable) {
          if (f2 instanceof DisplayPrioritySortable) {
            int result = ((DisplayPrioritySortable)f1).getPriority().compareTo(((DisplayPrioritySortable)f2).getPriority());
            if (result != 0) return result;
          }
          else {
            return 1;
          }
        }
        else if (f2 instanceof DisplayPrioritySortable) {
          return -1;
        }
        return f1.getPanelDisplayName().compareToIgnoreCase(f2.getPanelDisplayName());
      }
    });
    result.addAll(extensions);

    result.add(new DiffColorsPageFactory());
    result.add(new FileStatusColorsPageFactory());
    result.add(new ScopeColorsPageFactory());

    return result;
  }

  private static class FontConfigurableFactory implements ColorAndFontPanelFactory {
    @Override
    @NotNull
    public NewColorAndFontPanel createPanel(@NotNull ColorAndFontOptions options) {
      FontEditorPreview previewPanel = new FontEditorPreview(options, true);
      return new NewColorAndFontPanel(new SchemesPanel(options), new FontOptions(options), previewPanel, "Font", null, null){
        @Override
        public boolean containsFontOptions() {
          return true;
        }
      };
    }

    @Override
    @NotNull
    public String getPanelDisplayName() {
      return "Font";
    }
  }

   private static class ConsoleFontConfigurableFactory implements ColorAndFontPanelFactoryEx {
    @Override
    @NotNull
    public NewColorAndFontPanel createPanel(@NotNull ColorAndFontOptions options) {
      FontEditorPreview previewPanel = new FontEditorPreview(options, false) {
        @Override
        protected EditorColorsScheme updateOptionsScheme(EditorColorsScheme selectedScheme) {
          return ConsoleViewUtil.updateConsoleColorScheme(selectedScheme);
        }
      };
      return new NewColorAndFontPanel(new SchemesPanel(options), new ConsoleFontOptions(options), previewPanel, "Font", null, null){
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

     @Override
     public DisplayPriority getPriority() {
       return DisplayPriority.COMMON_SETTINGS;
     }
   }

  private class DiffColorsPageFactory implements ColorAndFontPanelFactory {
    @Override
    @NotNull
    public NewColorAndFontPanel createPanel(@NotNull ColorAndFontOptions options) {
      final DiffOptionsPanel optionsPanel = new DiffOptionsPanel(options);
      SchemesPanel schemesPanel = new SchemesPanel(options);
      PreviewPanel previewPanel;
      try {
        final DiffPreviewPanel diffPreviewPanel = new DiffPreviewPanel(myDisposable);
        diffPreviewPanel.setMergeRequest(null);
        schemesPanel.addListener(new ColorAndFontSettingsListener.Abstract(){
          @Override
          public void schemeChanged(final Object source) {
            diffPreviewPanel.setColorScheme(getSelectedScheme());
            optionsPanel.updateOptionsList();
            diffPreviewPanel.updateView();
          }
        } );
        previewPanel = diffPreviewPanel;
      }
      catch (FilesTooBigForDiffException e) {
        LOG.info(e);
        previewPanel = new PreviewPanel.Empty();
      }

      return new NewColorAndFontPanel(schemesPanel, optionsPanel, previewPanel, DIFF_GROUP, null, null);
    }

    @Override
    @NotNull
    public String getPanelDisplayName() {
      return DIFF_GROUP;
    }
  }

  private void initAll() {
    EditorColorsManager colorsManager = EditorColorsManager.getInstance();
    EditorColorsScheme[] allSchemes = colorsManager.getAllSchemes();

    mySchemes = new HashMap<String, MyColorScheme>();
    for (EditorColorsScheme allScheme : allSchemes) {
      MyColorScheme schemeDelegate = new MyColorScheme(allScheme);
      initScheme(schemeDelegate);
      mySchemes.put(schemeDelegate.getName(), schemeDelegate);
    }

    mySelectedScheme = mySchemes.get(EditorColorsManager.getInstance().getGlobalScheme().getName());
    assert mySelectedScheme != null : EditorColorsManager.getInstance().getGlobalScheme().getName() + "; myschemes=" + mySchemes;
  }

  private static void initScheme(MyColorScheme scheme) {
    ArrayList<EditorSchemeAttributeDescriptor> descriptions = new ArrayList<EditorSchemeAttributeDescriptor>();
    initPluggedDescriptions(descriptions, scheme);
    initDiffDescriptors(descriptions, scheme);
    initFileStatusDescriptors(descriptions, scheme);
    initScopesDescriptors(descriptions, scheme);

    scheme.setDescriptors(descriptions.toArray(new EditorSchemeAttributeDescriptor[descriptions.size()]));
  }

  private static void initPluggedDescriptions(ArrayList<EditorSchemeAttributeDescriptor> descriptions, MyColorScheme scheme) {
    ColorSettingsPage[] pages = ColorSettingsPages.getInstance().getRegisteredPages();
    for (ColorSettingsPage page : pages) {
      initDescriptions(page, descriptions, scheme);
    }
    for (ColorAndFontDescriptorsProvider provider : Extensions.getExtensions(ColorAndFontDescriptorsProvider.EP_NAME)) {
      initDescriptions(provider, descriptions, scheme);
    }
  }

  private static void initDescriptions(ColorAndFontDescriptorsProvider provider,
                                       ArrayList<EditorSchemeAttributeDescriptor> descriptions,
                                       MyColorScheme scheme) {
    String group = provider.getDisplayName();
    List<AttributesDescriptor> attributeDescriptors = ColorSettingsUtil.getAllAttributeDescriptors(provider);
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

  private static void initDiffDescriptors(ArrayList<EditorSchemeAttributeDescriptor> descriptions, MyColorScheme scheme) {
    DiffOptionsPanel.addSchemeDescriptions(descriptions, scheme);
  }

  private static void initFileStatusDescriptors(ArrayList<EditorSchemeAttributeDescriptor> descriptions, MyColorScheme scheme) {

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
  private static void initScopesDescriptors(ArrayList<EditorSchemeAttributeDescriptor> descriptions, MyColorScheme scheme) {
    Set<Pair<NamedScope,NamedScopesHolder>> namedScopes = new THashSet<Pair<NamedScope,NamedScopesHolder>>(new TObjectHashingStrategy<Pair<NamedScope,NamedScopesHolder>>() {
      @Override
      public int computeHashCode(final Pair<NamedScope, NamedScopesHolder> object) {
        return object.getFirst().getName().hashCode();
      }

      @Override
      public boolean equals(final Pair<NamedScope, NamedScopesHolder> o1, final Pair<NamedScope, NamedScopesHolder> o2) {
        return o1.getFirst().getName().equals(o2.getFirst().getName());
      }
    });
    Project[] projects = ProjectManager.getInstance().getOpenProjects();
    for (Project project : projects) {
      DaemonCodeAnalyzerImpl codeAnalyzer = (DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(project);
      List<Pair<NamedScope,NamedScopesHolder>> cachedScopes = codeAnalyzer.getScopeBasedHighlightingCachedScopes();
      namedScopes.addAll(cachedScopes);
    }

    List<Pair<NamedScope, NamedScopesHolder>> list = new ArrayList<Pair<NamedScope, NamedScopesHolder>>(namedScopes);

    Collections.sort(list, new Comparator<Pair<NamedScope,NamedScopesHolder>>() {
      @Override
      public int compare(final Pair<NamedScope,NamedScopesHolder> o1, final Pair<NamedScope,NamedScopesHolder> o2) {
        return o1.getFirst().getName().compareToIgnoreCase(o2.getFirst().getName());
      }
    });
    for (Pair<NamedScope,NamedScopesHolder> pair : list) {
      NamedScope namedScope = pair.getFirst();
      String name = namedScope.getName();
      TextAttributesKey textAttributesKey = getScopeTextAttributeKey(name);
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

  public static TextAttributesKey getScopeTextAttributeKey(final String scope) {
    return TextAttributesKey.find("SCOPE_KEY_" + scope);
  }

  private static void addEditorSettingDescription(ArrayList<EditorSchemeAttributeDescriptor> array,
                                                                     String name,
                                                                     String group,
                                                                     ColorKey backgroundKey,
                                                                     ColorKey foregroundKey,
                                                                     EditorColorsScheme scheme) {
    String type = null;
    if (foregroundKey != null) {
      type = foregroundKey.getExternalName();
    }
    else {
      if (backgroundKey != null) {
        type = backgroundKey.getExternalName();
      }
    }
    ColorAndFontDescription descr = new EditorSettingColorDescription(name, group, backgroundKey, foregroundKey, type, scheme);
    array.add(descr);
  }

  private static void addSchemedDescription(ArrayList<EditorSchemeAttributeDescriptor> array, String name, String group, TextAttributesKey key,
                                            MyColorScheme scheme,
                                            Icon icon,
                                            String toolTip) {
    ColorAndFontDescription descr = new SchemeTextAttributesDescription(name, group, key, scheme, icon, toolTip);
    array.add(descr);
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
        super.reset();
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
          if (myRootSchemesPanel != null) {
            myRootSchemesPanel.disposeUIResources();
          }
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

  public boolean currentSchemeIsReadOnly() {
    return isReadOnly(mySelectedScheme);
  }

  public boolean currentSchemeIsShared() {
    return ColorSettingsUtil.isSharedScheme(mySelectedScheme);
  }


  private static class SchemeTextAttributesDescription extends TextAttributesDescription {
    private final TextAttributes myAttributesToApply;
    private final TextAttributesKey key;
    private TextAttributes myFallbackAttributes;
    private Pair<ColorSettingsPage,AttributesDescriptor> myBaseAttributeDescriptor;

    private SchemeTextAttributesDescription(String name, String group, TextAttributesKey key, MyColorScheme  scheme, Icon icon,
                                           String toolTip) {
      super(name, group,
            getInitialAttributes(scheme, key).clone(),
            key, scheme, icon, toolTip);
      this.key = key;
      myAttributesToApply = getInitialAttributes(scheme, key);
      TextAttributesKey fallbackKey = key.getFallbackAttributeKey();
      if (fallbackKey != null) {
        myFallbackAttributes = scheme.getAttributes(fallbackKey);
        myBaseAttributeDescriptor = ColorSettingsPages.getInstance().getAttributeDescriptor(fallbackKey);
        if (myBaseAttributeDescriptor == null) {
          myBaseAttributeDescriptor =
            new Pair<ColorSettingsPage, AttributesDescriptor>(null, new AttributesDescriptor(fallbackKey.getExternalName(), fallbackKey));
        }
      }
      initCheckedStatus();
    }


    @NotNull
    private static TextAttributes getInitialAttributes(MyColorScheme scheme, TextAttributesKey key) {
      TextAttributes attributes = scheme.getAttributes(key);
      TextAttributesKey fallbackKey = key.getFallbackAttributeKey();
      if (fallbackKey != null && !scheme.containsKey(key)) {
        TextAttributes fallbackAttributes = scheme.getAttributes(fallbackKey);
        if (attributes != null && attributes == fallbackAttributes) {
          return new TextAttributes();
        }
      }
      return attributes != null ? attributes : new TextAttributes();
    }

    @Override
    public void apply(EditorColorsScheme scheme) {
      if (scheme == null) scheme = getScheme();
      if (myAttributesToApply != null) {
        scheme.setAttributes(key, getTextAttributes());
      }
    }

    @Override
    public boolean isModified() {
      return !Comparing.equal(myAttributesToApply, getTextAttributes());
    }

    @Override
    public boolean isErrorStripeEnabled() {
      return true;
    }

    @Override
    public boolean isInherited() {
      return myFallbackAttributes != null && getTextAttributes().isFallbackEnabled();
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
    private boolean isModified = false;
    private Color myColor;

    private GetSetColor(ColorKey key, EditorColorsScheme scheme) {
      myKey = key;
      myScheme = scheme;
      myColor = myScheme.getColor(myKey);
    }

    public Color getColor() {
      return myColor;
    }

    public void setColor(Color col) {
      if (getColor() == null || !getColor().equals(col)) {
        isModified = true;
        myColor = col;
      }
    }

    public void apply(EditorColorsScheme scheme) {
      if (scheme == null) scheme = myScheme;
      scheme.setColor(myKey, myColor);
    }

    public boolean isModified() {
      return isModified;
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
      return 0;
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
    return "reference.settingsdialog.IDE.editor.colors";
  }

  private static class MyColorScheme extends EditorColorsSchemeImpl {

    private EditorSchemeAttributeDescriptor[] myDescriptors;
    private String                            myName;
    private boolean myIsNew = false;

    private MyColorScheme(EditorColorsScheme parentScheme) {
      super(parentScheme, DefaultColorSchemesManager.getInstance());
      parentScheme.getFontPreferences().copyTo(getFontPreferences());
      setLineSpacing(parentScheme.getLineSpacing());

      parentScheme.getConsoleFontPreferences().copyTo(getConsoleFontPreferences());
      setConsoleLineSpacing(parentScheme.getConsoleLineSpacing());

      setQuickDocFontSize(parentScheme.getQuickDocFontSize());
      myName = parentScheme.getName();
      if (parentScheme instanceof ExternalizableScheme) {
        getExternalInfo().copy(((ExternalizableScheme)parentScheme).getExternalInfo());
      }
      initFonts();
    }

    @Override
    public String getName() {
      return myName;
    }

    @Override
    public void setName(String name) {
      myName = name;
    }

    public void setDescriptors(EditorSchemeAttributeDescriptor[] descriptors) {
      myDescriptors = descriptors;
    }

    public EditorSchemeAttributeDescriptor[] getDescriptors() {
      return myDescriptors;
    }

    public boolean isDefault() {
      return myParentScheme instanceof DefaultColorsScheme;
    }

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

    private boolean isFontModified() {
      if (!getFontPreferences().equals(myParentScheme.getFontPreferences())) return true;
      if (getLineSpacing() != myParentScheme.getLineSpacing()) return true;
      if (getQuickDocFontSize() != myParentScheme.getQuickDocFontSize()) return true;
      return false;
    }

    private boolean isConsoleFontModified() {
      if (!getConsoleFontPreferences().equals(myParentScheme.getConsoleFontPreferences())) return true;
      if (getConsoleLineSpacing() != myParentScheme.getConsoleLineSpacing()) return true;
      return false;
    }

    public void apply() {
      apply(myParentScheme);
    }

    public void apply(EditorColorsScheme scheme) {
      scheme.setFontPreferences(getFontPreferences());
      scheme.setLineSpacing(myLineSpacing);
      scheme.setQuickDocFontSize(getQuickDocFontSize());
      scheme.setConsoleFontPreferences(getConsoleFontPreferences());
      scheme.setConsoleLineSpacing(getConsoleLineSpacing());

      for (EditorSchemeAttributeDescriptor descriptor : myDescriptors) {
        descriptor.apply(scheme);
      }
    }

    @Override
    public Object clone() {
      return null;
    }

    public EditorColorsScheme getOriginalScheme() {
      return myParentScheme;
    }

    public void setIsNew() {
      myIsNew = true;
    }

    public boolean isNew() {
      return myIsNew;
    }

    @Override
    public String toString() {
      return "temporary scheme for " + myName;
    }
  }

  @Override
  @NotNull
  public String getId() {
    return getHelpTopic();
  }

  @Override
  @Nullable
  public Runnable enableSearch(final String option) {
    return null;
  }

  @Nullable
  public InnerSearchableConfigurable findSubConfigurable(final Class pageClass) {
    if (mySubPanelFactories == null) {
      buildConfigurables();
    }
    for (Map.Entry<ColorAndFontPanelFactory, InnerSearchableConfigurable> entry : mySubPanelFactories.entrySet()) {
      if (pageClass.isInstance(entry.getValue().createPanel().getSettingsPage())) {
        return entry.getValue();
      }
    }
    return null;
  }

  @Nullable
  public NewColorAndFontPanel findPage(String pageName) {
    if (mySubPanelFactories == null) {
      buildConfigurables();
    }
    for (InnerSearchableConfigurable configurable : mySubPanelFactories.values()) {
      if (configurable.getDisplayName().equals(pageName)) {
        return configurable.createPanel();
      }
    }
    return null;
  }

  private class InnerSearchableConfigurable implements SearchableConfigurable, OptionsContainingConfigurable, NoScroll {
    private NewColorAndFontPanel mySubPanel;
    private boolean mySubInitInvoked = false;
    private final ColorAndFontPanelFactory myFactory;

    private InnerSearchableConfigurable(@NotNull ColorAndFontPanelFactory factory) {
      myFactory = factory;
    }

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

    @Override
    public Set<String> processListOptions() {
      return createPanel().processListOptions();
    }

    @NonNls
    @Override
    public String toString() {
      return "Color And Fonts for "+getDisplayName();
    }
  }
}
