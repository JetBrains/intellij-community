package com.intellij.psi.impl.source.codeStyle;

import com.intellij.CommonBundle;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ExportableApplicationComponent;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.SettingsSavingComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.SchemeReaderWriter;
import com.intellij.openapi.options.SchemesManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiBundle;
import com.intellij.psi.codeStyle.CodeStyleScheme;
import com.intellij.psi.codeStyle.CodeStyleSchemes;
import com.intellij.util.containers.HashMap;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

/**
 * @author MYakovlev
 *         Date: Jul 16, 2002
 */
public class CodeStyleSchemesImpl extends CodeStyleSchemes implements ExportableApplicationComponent,JDOMExternalizable,
                                                                      SettingsSavingComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.codeStyle.CodeStyleSchemesImpl");
  private HashMap<String, CodeStyleScheme> mySchemes = new HashMap<String, CodeStyleScheme>();   // name -> scheme
  private CodeStyleScheme myCurrentScheme;

  @NonNls private static final String DEFAULT_SCHEME_NAME = "Default";

  public String CURRENT_SCHEME_NAME = DEFAULT_SCHEME_NAME;
  private boolean myIsInitialized = false;
  @NonNls private static final String CODESTYLES_DIRECTORY = "codestyles";

  private final SchemesManager mySchemesManager;
  private final SchemeReaderWriter<CodeStyleScheme> myReaderWriter;
  private static final String FILE_SPEC = "$ROOT_CONFIG$/" + CODESTYLES_DIRECTORY;

  public CodeStyleSchemesImpl(SchemesManager schemesManager) {
    mySchemesManager = schemesManager;
    myReaderWriter = new SchemeReaderWriter<CodeStyleScheme>() {
      public CodeStyleScheme readScheme(final Document schemeContent, final File file) throws IOException, JDOMException, InvalidDataException {
        return CodeStyleSchemeImpl.readScheme(schemeContent);
      }

      public Document writeScheme(final CodeStyleScheme scheme) throws WriteExternalException {
        return ((CodeStyleSchemeImpl)scheme).saveToDocument();
      }

      public void showReadErrorMessage(final Exception e, final String schemeName, final String filePath) {
        Messages.showErrorDialog(PsiBundle.message("codestyle.cannot.read.scheme.file.message", filePath),
                                 PsiBundle.message("codestyle.cannot.read.scheme.file.title"));
      }

      public void showWriteErrorMessage(final Exception e, final String schemeName, final String filePath) {
        Messages.showErrorDialog(PsiBundle.message("codestyle.cannot.save.scheme.file", filePath, e.getLocalizedMessage()), CommonBundle.getErrorTitle());
      }

      public boolean shouldBeSaved(final CodeStyleScheme scheme) {
        return !scheme.isDefault();
      }
    };
  }

  public String getComponentName() {
    return "CodeStyleSchemes";
  }

  public void initComponent() {
    init();
    addScheme(new CodeStyleSchemeImpl(DEFAULT_SCHEME_NAME, true, null));
    CodeStyleScheme current = findSchemeByName(CURRENT_SCHEME_NAME);
    if (current == null) current = getDefaultScheme();
    setCurrentScheme(current);
  }

  public void disposeComponent() {
  }

  public CodeStyleScheme[] getSchemes() {
    final Collection<CodeStyleScheme> schemes = mySchemes.values();
    return schemes.toArray(new CodeStyleScheme[schemes.size()]);
  }

  public CodeStyleScheme getCurrentScheme() {
    return myCurrentScheme;
  }

  public void setCurrentScheme(CodeStyleScheme scheme) {
    myCurrentScheme = scheme;
    CURRENT_SCHEME_NAME = scheme.getName();
  }

  public CodeStyleScheme createNewScheme(String preferredName, CodeStyleScheme parentScheme) {
    String name;
    if (preferredName == null) {
      // Generate using parent name
      name = null;
      for (int i = 1; name == null; i++) {
        String currName = parentScheme.getName() + " (" + i + ")";
        if (null == findSchemeByName(currName)) {
          name = currName;
        }
      }
    }
    else {
      name = null;
      for (int i = 0; name == null; i++) {
        String currName = i == 0 ? preferredName : preferredName + " (" + i + ")";
        if (null == findSchemeByName(currName)) {
          name = currName;
        }
      }
    }
    return new CodeStyleSchemeImpl(name, false, parentScheme);
  }

  public void deleteScheme(CodeStyleScheme scheme) {
    if (scheme.isDefault()) {
      throw new IllegalArgumentException("Unable to delete default scheme!");
    }
    CodeStyleSchemeImpl currScheme = (CodeStyleSchemeImpl)getCurrentScheme();
    if (currScheme == scheme) {
      CodeStyleScheme newCurrentScheme = getDefaultScheme();
      if (newCurrentScheme == null) {
        throw new IllegalStateException("Unable to load default scheme!");
      }
      setCurrentScheme(newCurrentScheme);
    }
    mySchemes.remove(scheme.getName());
  }

  public CodeStyleScheme getDefaultScheme() {
    return findSchemeByName(DEFAULT_SCHEME_NAME);
  }

  public CodeStyleScheme findSchemeByName(String name) {
    return mySchemes.get(name);
  }

  public void addScheme(CodeStyleScheme scheme) {
    String name = scheme.getName();
    if (mySchemes.containsKey(name)) {
      LOG.error("Not unique scheme name: " + name);
    }
    mySchemes.put(name, scheme);
  }

  protected void removeScheme(CodeStyleScheme scheme) {
    mySchemes.remove(scheme.getName());
  }

  public void readExternal(Element element) throws InvalidDataException {
    init();
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  private void init() {
    if (myIsInitialized) return;
    myIsInitialized = true;
    mySchemes.clear();

    final Collection<CodeStyleScheme> readSchemes = mySchemesManager.loadSchemes(FILE_SPEC, myReaderWriter, RoamingType.PER_USER);

    for (CodeStyleScheme scheme : readSchemes) {
      addScheme(scheme);
    }

    final CodeStyleScheme[] schemes = getSchemes();
    for (CodeStyleScheme scheme : schemes) {
      ((CodeStyleSchemeImpl)scheme).init(this);
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
  }

  public void save() {
    try {
      mySchemesManager.saveSchemes(mySchemes.values(), FILE_SPEC, myReaderWriter,
                                   RoamingType.PER_USER);
    }
    catch (WriteExternalException e) {
      //ignore
    }
  }

  @NotNull
  public File[] getExportFiles() {
    return new File[]{getDir(true), PathManager.getDefaultOptionsFile()};
  }

  @NotNull
  public String getPresentableName() {
    return PsiBundle.message("codestyle.export.display.name");
  }

  private static File getDir(boolean create) {
    String directoryPath = PathManager.getConfigPath() + File.separator + CODESTYLES_DIRECTORY;
    File directory = new File(directoryPath);
    if (!directory.exists()) {
      if (!create) return null;
      if (!directory.mkdir()) {
        Messages.showErrorDialog(PsiBundle.message("codestyle.cannot.save.settings.directory.cant.be.created.message", directoryPath),
                                 PsiBundle.message("codestyle.cannot.save.settings.directory.cant.be.created.title"));
        return null;
      }
    }
    return directory;
  }
}
