package org.jetbrains.platform.loader.impl.repository;

import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.platform.loader.repository.RuntimeModuleDescriptor;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
public class ModuleXmlParser {
  public static RuntimeModuleDescriptor parseModuleXml(XMLInputFactory factory, InputStream inputStream, File baseDir) throws XMLStreamException {
    XMLStreamReader reader = factory.createXMLStreamReader(inputStream);
    List<String> dependencies = new ArrayList<String>();
    List<ResourceRoot> resources = new ArrayList<ResourceRoot>();
    String moduleName = null;
    int level = 0;
    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT) {
        level++;
        String tagName = reader.getLocalName();
        if (level == 1 && tagName.equals("module")) {
          moduleName = expectFirstAttribute(reader, "name");
        }
        else if (level == 3 & tagName.equals("module")) {
          dependencies.add(expectFirstAttribute(reader, "name"));
        }
        else if (level == 3 && tagName.equals("resource-root")) {
          String relativePath = expectFirstAttribute(reader, "path");
          resources.add(createResourceRoot(baseDir, relativePath));
        }
      }
      else if (event == XMLStreamConstants.END_ELEMENT) {
        level--;
        if (level == 0) {
          break;
        }
      }
    }
    reader.close();
    return new RuntimeModuleDescriptorImpl(moduleName, resources, dependencies);
  }

  private static String expectFirstAttribute(XMLStreamReader reader, String attributeName) throws XMLStreamException {
    String name = reader.getAttributeLocalName(0);
    if (!attributeName.equals(name)) {
      throw new XMLStreamException("incorrect first attribute: " + attributeName + " expected but " + name + " found");
    }
    return reader.getAttributeValue(0);
  }

  private static ResourceRoot createResourceRoot(File baseDir, String relativePath) {
    if (baseDir.isFile()) {
      return new JarResourceRoot(baseDir);
    }

    File file = new File(FileUtil.toCanonicalPath(new File(baseDir, relativePath).getAbsolutePath()));
    if (file.isDirectory()) {
      return new DirectoryResourceRoot(file);
    }
    else {
      return new JarResourceRoot(file);
    }
  }
}
