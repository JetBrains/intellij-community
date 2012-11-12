package org.jetbrains.jps.model.serialization;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.jps.model.JpsElement;

import java.io.File;
import java.io.IOException;

/**
 * @author nik
 */
public abstract class JpsLoaderBase {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.model.serialization.JpsLoaderBase");
  private final JpsMacroExpander myMacroExpander;

  protected JpsLoaderBase(JpsMacroExpander macroExpander) {
    myMacroExpander = macroExpander;
  }

  protected Element loadRootElement(final File file) {
    return loadRootElement(file, myMacroExpander);
  }

  protected <E extends JpsElement> void loadComponents(File dir,
                                                       final String defaultFileName,
                                                       JpsElementExtensionSerializerBase<E> serializer,
                                                       final E element) {
    String fileName = serializer.getConfigFileName();
    File configFile = new File(dir, fileName != null ? fileName : defaultFileName);
    if (configFile.exists()) {
      Element componentTag = JDomSerializationUtil.findComponent(loadRootElement(configFile), serializer.getComponentName());
      if (componentTag != null) {
        serializer.loadExtension(element, componentTag);
      }
      else {
        serializer.loadExtensionWithDefaultSettings(element);
      }
    }
    else {
      LOG.debug("Cannot load component " + serializer.getComponentName() + ": " + configFile + " doesn't exist");
    }
  }

  protected static Element loadRootElement(final File file, final JpsMacroExpander macroExpander) {
    try {
      final Element element = JDOMUtil.loadDocument(file).getRootElement();
      macroExpander.substitute(element, SystemInfo.isFileSystemCaseSensitive);
      return element;
    }
    catch (JDOMException e) {
      throw new RuntimeException("Cannot parse xml file " + file.getAbsolutePath() + ": " + e.getMessage(), e);
    }
    catch (IOException e) {
      throw new RuntimeException("Cannot read file " + file.getAbsolutePath() + ": " + e.getMessage(), e);
    }
  }

  protected static boolean isXmlFile(File file) {
    return file.isFile() && FileUtil.getExtension(file.getName()).equalsIgnoreCase("xml");
  }
}
