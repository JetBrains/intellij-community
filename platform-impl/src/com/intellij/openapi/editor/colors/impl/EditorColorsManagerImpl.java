/**
 * @author Yura Cangea
 */
package com.intellij.openapi.editor.colors.impl;

import com.intellij.CommonBundle;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ExportableApplicationComponent;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.SettingsSavingComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.EditorColorsListener;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.ex.DefaultColorSchemesManager;
import com.intellij.openapi.options.OptionsBundle;
import com.intellij.openapi.options.Scheme;
import com.intellij.openapi.options.SchemeProcessor;
import com.intellij.openapi.options.SchemesManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.NamedJDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class EditorColorsManagerImpl extends EditorColorsManager implements NamedJDOMExternalizable,
                                                                            ExportableApplicationComponent, SettingsSavingComponent {
  private static final Logger LOG = Logger.getInstance(
    "#com.intellij.openapi.editor.colors.impl.EditorColorsManagerImpl");

  private Map<String, EditorColorsScheme> mySchemesMap = new com.intellij.util.containers.HashMap<String, EditorColorsScheme>();
  private Collection<EditorColorsListener> myListeners = new ArrayList<EditorColorsListener>();

  private EditorColorsScheme myGlobalScheme;

  @NonNls private static final String NODE_NAME = "global_color_scheme";
  @NonNls private static final String SCHEME_NODE_NAME = "scheme";

  private String myGlobalSchemeName;
  public boolean USE_ONLY_MONOSPACED_FONTS = true;
  private DefaultColorSchemesManager myDefaultColorSchemesManager;
  private final SchemesManager mySchemesManager;
  @NonNls private static final String XML_EXT = ".xml";
  @NonNls private static final String NAME_ATTR = "name";
  private static final String FILE_SPEC = "$ROOT_CONFIG$/colors";
  private final SchemeProcessor<EditorColorsScheme> mySchemeProcessor;

  public EditorColorsManagerImpl(DefaultColorSchemesManager defaultColorSchemesManager, SchemesManager schemesManager) {
    myDefaultColorSchemesManager = defaultColorSchemesManager;
    mySchemesManager = schemesManager;
    addDefaultSchemes();
        mySchemeProcessor = new SchemeProcessor<EditorColorsScheme>() {
      public EditorColorsScheme readScheme(final Document document, final File file) throws InvalidDataException, IOException, JDOMException {
        Element root = document.getRootElement();

        if (root == null || !SCHEME_NODE_NAME.equals(root.getName())) {
          throw new InvalidDataException();
        }

        EditorColorsSchemeImpl scheme = new EditorColorsSchemeImpl(null, DefaultColorSchemesManager.getInstance());
        scheme.readExternal(root);
        return scheme;
      }

      public Document writeScheme(final EditorColorsScheme scheme) throws WriteExternalException {
        Element root = new Element(SCHEME_NODE_NAME);
        try {
          scheme.writeExternal(root);
        }
        catch (WriteExternalException e) {
          LOG.error(e);
          return null;
        }

        return new Document(root);
      }

      public void showWriteErrorMessage(final Exception e, final String schemeName, final String filePath) {
        LOG.debug(e);
      }

      public void showReadErrorMessage(final Exception e,
                                       final String schemeName,
                                       final String filePath) {
        Messages.showErrorDialog(CommonBundle.message("error.reading.color.scheme.from.file.error.message", schemeName),
                                 CommonBundle.message("corrupted.scheme.file.message.title"));

      }

      public boolean shouldBeSaved(final EditorColorsScheme scheme) {
        return !(scheme instanceof DefaultColorsScheme);
      }
    };
    loadAllSchemes();
    setGlobalScheme(myDefaultColorSchemesManager.getAllSchemes()[0]);
  }

  // -------------------------------------------------------------------------
  // ApplicationComponent interface implementation
  // -------------------------------------------------------------------------

  public void disposeComponent() {
  }

  public void initComponent() {
  }

  // -------------------------------------------------------------------------
  // Schemes manipulation routines
  // -------------------------------------------------------------------------

  public void addColorsScheme(EditorColorsScheme scheme) {
    if (!isDefaultScheme(scheme) && scheme.getName().trim().length() > 0) {
      mySchemesMap.put(scheme.getName(), scheme);
    }
  }

  public void removeAllSchemes() {
    mySchemesMap.clear();
    addDefaultSchemes();
  }

  private void addDefaultSchemes() {
    DefaultColorsScheme[] allDefaultSchemes = myDefaultColorSchemesManager.getAllSchemes();
    for (DefaultColorsScheme defaultScheme : allDefaultSchemes) {
      mySchemesMap.put(defaultScheme.getName(), defaultScheme);
    }
  }

  // -------------------------------------------------------------------------
  // Getters & Setters
  // -------------------------------------------------------------------------

  public EditorColorsScheme[] getAllSchemes() {
    ArrayList<EditorColorsScheme> schemes = new ArrayList<EditorColorsScheme>(mySchemesMap.values());
    Collections.sort(schemes, new Comparator() {
      public int compare(Object o1, Object o2) {
        EditorColorsScheme s1 = (EditorColorsScheme)o1;
        EditorColorsScheme s2 = (EditorColorsScheme)o2;

        if (isDefaultScheme(s1) && !isDefaultScheme(s2)) return -1;
        if (!isDefaultScheme(s1) && isDefaultScheme(s2)) return 1;

        return s1.getName().compareToIgnoreCase(s2.getName());
      }
    });

    return schemes.toArray(new EditorColorsScheme[schemes.size()]);
  }

  public void setGlobalScheme(EditorColorsScheme scheme) {
    myGlobalScheme = scheme == null ? DefaultColorSchemesManager.getInstance().getAllSchemes()[0] : scheme;
    fireChanges(scheme);
  }

  public EditorColorsScheme getGlobalScheme() {
    return myGlobalScheme;
  }

  public EditorColorsScheme getScheme(String schemeName) {
    return mySchemesMap.get(schemeName);
  }

  private void fireChanges(EditorColorsScheme scheme) {
    EditorColorsListener[] colorsListeners = myListeners.toArray(new EditorColorsListener[myListeners.size()]);
    for (EditorColorsListener colorsListener : colorsListeners) {
      colorsListener.globalSchemeChange(scheme);
    }
  }

  // -------------------------------------------------------------------------
  // Routines responsible for loading & saving colors schemes.
  // -------------------------------------------------------------------------

  private void loadAllSchemes() {

    final Collection<? extends Scheme> schemes = mySchemesManager.loadSchemes(FILE_SPEC, mySchemeProcessor, RoamingType.PER_USER);

    for (Scheme scheme : schemes) {
      addColorsScheme((EditorColorsScheme)scheme);
    }

  }

  public void saveAllSchemes() throws IOException {
    try {
      mySchemesManager.saveSchemes(mySchemesMap.values(), FILE_SPEC, mySchemeProcessor, RoamingType.PER_USER);
    }
    catch (WriteExternalException e) {
      LOG.error(e);
    }
  }

  public void save() {
    try {
      saveAllSchemes();
    }
    catch (IOException e) {
      //ignore
    }
  }

  private static File getColorsDir(boolean create) {
    @NonNls String directoryPath = PathManager.getConfigPath() + File.separator + "colors";
    File directory = new File(directoryPath);
    if (!directory.exists()) {
      if (!create) return null;
      if (!directory.mkdir()) {
        LOG.error("Cannot create directory: " + directory.getAbsolutePath());
        return null;
      }
    }
    return directory;
  }


  public void addEditorColorsListener(EditorColorsListener listener) {
    myListeners.add(listener);
  }

  public void removeEditorColorsListener(EditorColorsListener listener) {
    myListeners.remove(listener);
  }

  public void setUseOnlyMonospacedFonts(boolean b) {
    USE_ONLY_MONOSPACED_FONTS = b;
  }

  public boolean isUseOnlyMonospacedFonts() {
    return USE_ONLY_MONOSPACED_FONTS;
  }

  public String getExternalFileName() {
    return "colors.scheme";
  }

  @NotNull
  public File[] getExportFiles() {
    return new File[]{getColorsDir(true), PathManager.getOptionsFile(this)};
  }

  @NotNull
  public String getPresentableName() {
    return OptionsBundle.message("options.color.schemes.presentable.name");
  }

  public void readExternal(Element parentNode) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, parentNode);
    Element element = parentNode.getChild(NODE_NAME);
    if (element != null) {
      String name = element.getAttributeValue(NAME_ATTR);
      if (name != null && !"".equals(name.trim())) {
        myGlobalSchemeName = name;
      }
    }

    initGlobalScheme();
  }

  private void initGlobalScheme() {
    if (myGlobalSchemeName != null) {
      setGlobalSchemeByName(myGlobalSchemeName);
    }
    else {
      setGlobalScheme(myDefaultColorSchemesManager.getAllSchemes()[0]);
    }
  }

  private void setGlobalSchemeByName(String schemeName) {
    setGlobalScheme(mySchemesMap.get(schemeName));
  }

  public void writeExternal(Element parentNode) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, parentNode);
    if (myGlobalScheme != null) {
      Element element = new Element(NODE_NAME);
      element.setAttribute(NAME_ATTR, myGlobalScheme.getName());
      parentNode.addContent(element);
    }
  }

  public boolean isDefaultScheme(EditorColorsScheme scheme) {
    return scheme instanceof DefaultColorsScheme;
  }

  public String getComponentName() {
    return "EditorColorsManagerImpl";
  }

  public SchemeProcessor<EditorColorsScheme> getSchemesProcessor() {
    return mySchemeProcessor;
  }
}