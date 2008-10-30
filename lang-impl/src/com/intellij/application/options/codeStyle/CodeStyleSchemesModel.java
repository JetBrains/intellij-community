package com.intellij.application.options.codeStyle;

import com.intellij.openapi.options.SchemesManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.codeStyle.CodeStyleScheme;
import com.intellij.psi.codeStyle.CodeStyleSchemes;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.impl.source.codeStyle.CodeStyleSchemeImpl;
import com.intellij.psi.impl.source.codeStyle.CodeStyleSchemesImpl;
import com.intellij.util.EventDispatcher;

import java.util.*;


public class CodeStyleSchemesModel {

  private final List<CodeStyleScheme> mySchemes = new ArrayList<CodeStyleScheme>();
  private CodeStyleScheme myGlobalSelected;
  private CodeStyleSchemeImpl myProjectScheme;
  private final CodeStyleScheme myDefault;
  private final Map<CodeStyleScheme, CodeStyleSettings> mySettingsToClone = new HashMap<CodeStyleScheme, CodeStyleSettings>();

  private final EventDispatcher<CodeStyleSettingsListener> myDispatcher = EventDispatcher.create(CodeStyleSettingsListener.class);
  private final Project myProject;
  private boolean myUsePerProjectSettings;

  public CodeStyleSchemesModel(Project project) {
    myProject = project;
    myProjectScheme = new CodeStyleSchemeImpl("Project Scheme", false, CodeStyleSchemes.getInstance().getDefaultScheme());
    reset();
    myDefault = CodeStyleSchemes.getInstance().getDefaultScheme();
  }

  public void selectScheme(final CodeStyleScheme selected, Object source) {
    if (myGlobalSelected != selected && selected != myProjectScheme) {
      myGlobalSelected = selected;
      myDispatcher.getMulticaster().currentSchemeChanged(source);
    }
  }

  public void addScheme(final CodeStyleScheme newScheme, boolean changeSelection) {
    mySchemes.add(newScheme);
    myDispatcher.getMulticaster().schemeListChanged();
    if (changeSelection) {
      selectScheme(newScheme, this);
    }
  }

  public void removeScheme(final CodeStyleScheme scheme) {
    mySchemes.remove(scheme);
    myDispatcher.getMulticaster().schemeListChanged();
    if (myGlobalSelected == scheme) {
      selectScheme(myDefault, this);
    }
  }

  public CodeStyleSettings getCloneSettings(final CodeStyleScheme scheme) {
    if (!mySettingsToClone.containsKey(scheme)) {
      mySettingsToClone.put(scheme, scheme.getCodeStyleSettings().clone());
    }
    return mySettingsToClone.get(scheme);
  }

  public CodeStyleScheme getSelectedScheme(){
    if (myUsePerProjectSettings) {
      return myProjectScheme;
    }
    return myGlobalSelected;
  }

  public void addListener(CodeStyleSettingsListener listener) {
    myDispatcher.addListener(listener);
  }

  public List<CodeStyleScheme> getSchemes() {
    return Collections.unmodifiableList(mySchemes);
  }

  public void reset() {
    myUsePerProjectSettings = getProjectSettings().USE_PER_PROJECT_SETTINGS;

    CodeStyleScheme[] allSchemes = CodeStyleSchemes.getInstance().getSchemes();
    mySettingsToClone.clear();
    mySchemes.clear();
    mySchemes.addAll(Arrays.asList(allSchemes));
    myGlobalSelected = CodeStyleSchemes.getInstance().getCurrentScheme();

    CodeStyleSettings perProjectSettings = getProjectSettings().PER_PROJECT_SETTINGS;
    if (perProjectSettings != null) {
      myProjectScheme.setCodeStyleSettings(perProjectSettings);
    }


    myDispatcher.getMulticaster().schemeListChanged();
    myDispatcher.getMulticaster().currentSchemeChanged(this);

  }

  public boolean isUsePerProjectSettings() {
    return myUsePerProjectSettings;
  }

  public void setUsePerProjectSettings(final boolean usePerProjectSettings) {
    if (myUsePerProjectSettings != usePerProjectSettings) {
      myUsePerProjectSettings = usePerProjectSettings;
      myDispatcher.getMulticaster().usePerProjectSettingsOptionChanged();
      myDispatcher.getMulticaster().currentSchemeChanged(this);
    }

  }

  private CodeStyleSettingsManager getProjectSettings() {
    return CodeStyleSettingsManager.getInstance(myProject);
  }

  public boolean isSchemeListModified() {
    if (getProjectSettings().USE_PER_PROJECT_SETTINGS != myUsePerProjectSettings) return true;
    if (!myUsePerProjectSettings && (getSelectedScheme() != CodeStyleSchemes.getInstance().getCurrentScheme())) return true;
    Set<CodeStyleScheme> configuredSchemesSet = new HashSet<CodeStyleScheme>(getSchemes());
    Set<CodeStyleScheme> savedSchemesSet = new HashSet<CodeStyleScheme>(Arrays.asList(CodeStyleSchemes.getInstance().getSchemes()));
    if (!configuredSchemesSet.equals(savedSchemesSet)) return true;
    return false;
  }

  public void apply() {
    getProjectSettings().USE_PER_PROJECT_SETTINGS = myUsePerProjectSettings;
    getProjectSettings().PER_PROJECT_SETTINGS = myProjectScheme.getCodeStyleSettings();

    final CodeStyleScheme[] savedSchemes = CodeStyleSchemes.getInstance().getSchemes();
    final Set<CodeStyleScheme> savedSchemesSet = new HashSet<CodeStyleScheme>(Arrays.asList(savedSchemes));
    List<CodeStyleScheme> configuredSchemes = getSchemes();

    for (CodeStyleScheme savedScheme : savedSchemes) {
      if (!configuredSchemes.contains(savedScheme)) {
        CodeStyleSchemes.getInstance().deleteScheme(savedScheme);
      }
    }

    for (CodeStyleScheme configuredScheme : configuredSchemes) {
      if (!savedSchemesSet.contains(configuredScheme)) {
        CodeStyleSchemes.getInstance().addScheme(configuredScheme);
      }
    }

    CodeStyleSchemes.getInstance().setCurrentScheme(myGlobalSelected);
  }

  static SchemesManager<CodeStyleScheme, CodeStyleSchemeImpl> getSchemesManager() {
    return ((CodeStyleSchemesImpl) CodeStyleSchemes.getInstance()).getSchemesManager();
  }

  public static boolean cannotBeModified(final CodeStyleScheme currentScheme) {
    return currentScheme.isDefault() || getSchemesManager().isShared(currentScheme);
  }

  public static boolean cannotBeDeleted(final CodeStyleScheme currentScheme) {
    return currentScheme.isDefault();
  }

  public void fireCurrentSettingsChanged() {
    myDispatcher.getMulticaster().currentSettingsChanged();
  }

  public CodeStyleScheme getSelectedGlobalScheme() {
    return myGlobalSelected;
  }
}
