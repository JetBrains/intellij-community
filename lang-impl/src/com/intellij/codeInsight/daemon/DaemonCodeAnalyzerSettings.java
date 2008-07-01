package com.intellij.codeInsight.daemon;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;

@State(
  name="DaemonCodeAnalyzerSettings",
  storages= {
    @Storage(
      id="other",
      file = "$APP_CONFIG$/editor.codeinsight.xml"
    )}
)
public class DaemonCodeAnalyzerSettings implements PersistentStateComponent<Element>, Cloneable, ExportableComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings");
  @NonNls private static final String ROOT_TAG = "root";
  @NonNls private static final String PROFILE_ATT = "profile";
  @NonNls public static final String DEFAULT_PROFILE_ATT = "Default";
  @NonNls public static final String PROFILE_COPY_NAME = "copy";
  private InspectionProfileManager myManager;


  public DaemonCodeAnalyzerSettings(InspectionProfileManager manager) {
    myManager = manager;
  }

  public static DaemonCodeAnalyzerSettings getInstance() {
    return ServiceManager.getService(DaemonCodeAnalyzerSettings.class);
  }

  @NotNull
  public File[] getExportFiles() {
    return new File[]{PathManager.getOptionsFile("editor.codeinsight")};
  }

  @NotNull
  public String getPresentableName() {
    return DaemonBundle.message("error.highlighting.settings");
  }

  public boolean NEXT_ERROR_ACTION_GOES_TO_ERRORS_FIRST = false;
  public int AUTOREPARSE_DELAY = 300;
  public boolean SHOW_ADD_IMPORT_HINTS = true;
  @NonNls public String NO_AUTO_IMPORT_PATTERN = "[a-z].?";
  public boolean SUPPRESS_WARNINGS = true;
  public boolean SHOW_METHOD_SEPARATORS = false;
  public int ERROR_STRIPE_MARK_MIN_HEIGHT = 3;

  public boolean isCodeHighlightingChanged(DaemonCodeAnalyzerSettings oldSettings) {
    try {
      Element rootNew = new Element(ROOT_TAG);
      writeExternal(rootNew);
      Element rootOld = new Element(ROOT_TAG);
      oldSettings.writeExternal(rootOld);

      return !JDOMUtil.areElementsEqual(rootOld, rootNew);
    }
    catch (WriteExternalException e) {
      LOG.error(e);
    }

    return false;
  }

  public Object clone() {
    DaemonCodeAnalyzerSettings settings = new DaemonCodeAnalyzerSettings(myManager);
    settings.AUTOREPARSE_DELAY = AUTOREPARSE_DELAY;
    settings.SHOW_ADD_IMPORT_HINTS = SHOW_ADD_IMPORT_HINTS;
    settings.SHOW_METHOD_SEPARATORS = SHOW_METHOD_SEPARATORS;
    settings.NO_AUTO_IMPORT_PATTERN = NO_AUTO_IMPORT_PATTERN;
    return settings;
  }

  public Element getState() {
    Element e = new Element("state");
    try {
      writeExternal(e);
    }
    catch (WriteExternalException ex) {
      LOG.error(ex);
    }
    return e;
  }

  public void loadState(final Element state) {
    try {
      readExternal(state);
    }
    catch (InvalidDataException e) {
      LOG.error(e);
    }
  }

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
    myManager.getConverter().storeEditorHighlightingProfile(element);
    myManager.setRootProfile(element.getAttributeValue(PROFILE_ATT));
  }

  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
    element.setAttribute(PROFILE_ATT, myManager.getRootProfile().getName());
  }

  public boolean isImportHintEnabled() {
    return SHOW_ADD_IMPORT_HINTS;
  }

  public void setImportHintEnabled(boolean isImportHintEnabled) {
    SHOW_ADD_IMPORT_HINTS = isImportHintEnabled;
  }
}
