package org.jetbrains.jps.model.serialization;

import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;

/**
 * @author nik
 */
public abstract class JpsLoaderBase {
  private final JpsMacroExpander myMacroExpander;

  protected JpsLoaderBase(JpsMacroExpander macroExpander) {
    myMacroExpander = macroExpander;
  }

  protected Element loadRootElement(final File file) {
    try {
      final Element element = JDOMUtil.loadDocument(file).getRootElement();
      myMacroExpander.substitute(element, SystemInfo.isFileSystemCaseSensitive);
      return element;
    }
    catch (JDOMException e) {
      throw new RuntimeException(e);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  protected static boolean isXmlFile(File file) {
    return file.isFile() && FileUtil.getNameWithoutExtension(file).equalsIgnoreCase("xml");
  }

  @Nullable
  public static Element findComponent(Element root, String componentName) {
    for (Element element : JDOMUtil.getChildren(root, "component")) {
      if (componentName.equals(element.getAttributeValue("name"))) {
        return element;
      }
    }
    return null;
  }
}
