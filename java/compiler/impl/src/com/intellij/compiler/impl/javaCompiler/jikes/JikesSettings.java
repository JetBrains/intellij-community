package com.intellij.compiler.impl.javaCompiler.jikes;

import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import java.util.StringTokenizer;

@State(
  name = "JikesSettings",
  storages = {
    @Storage(id = "default", file = "$PROJECT_FILE$")
   ,@Storage(id = "dir", file = "$PROJECT_CONFIG_DIR$/compiler.xml", scheme = StorageScheme.DIRECTORY_BASED)
    }
)
public class JikesSettings implements PersistentStateComponent<Element> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.impl.javaCompiler.jikes.JikesSettings");

  public String JIKES_PATH = "";
  public boolean DEBUGGING_INFO = true;
  public boolean DEPRECATION = true;
  public boolean GENERATE_NO_WARNINGS = false;
  public boolean IS_EMACS_ERRORS_MODE = true;

  public String ADDITIONAL_OPTIONS_STRING = "";

  public Element getState() {
    try {
      final Element e = new Element("state");
      writeExternal(e);
      return e;
    }
    catch (WriteExternalException e1) {
      LOG.error(e1);
      return null;
    }
  }

  public void loadState(Element state) {
    try {
      readExternal(state);
    }
    catch (InvalidDataException e) {
      LOG.error(e);
    }
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public @NonNls String getOptionsString() {
    StringBuffer options = new StringBuffer();
    if(DEBUGGING_INFO) {
      options.append("-g ");
    }
    if(DEPRECATION) {
      options.append("-deprecation ");
    }
    if(GENERATE_NO_WARNINGS) {
      options.append("-nowarn ");
    }
    /*
    if(IS_INCREMENTAL_MODE) {
      options.append("++ ");
    }
    */
    if(IS_EMACS_ERRORS_MODE) {
      options.append("+E ");
    }

    StringTokenizer tokenizer = new StringTokenizer(ADDITIONAL_OPTIONS_STRING, " \t\r\n");
    while(tokenizer.hasMoreTokens()) {
      String token = tokenizer.nextToken();
      if("-g".equals(token)) {
        continue;
      }
      if("-deprecation".equals(token)) {
        continue;
      }
      if("-nowarn".equals(token)) {
        continue;
      }
      if("++".equals(token)) {
        continue;
      }
      if("+M".equals(token)) {
        continue;
      }
      if("+F".equals(token)) {
        continue;
      }
      if("+E".equals(token)) {
        continue;
      }
      options.append(token);
      options.append(" ");
    }
    return options.toString();
  }

  public static JikesSettings getInstance(Project project) {
    return ServiceManager.getService(project, JikesSettings.class);
  }

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
  }
}