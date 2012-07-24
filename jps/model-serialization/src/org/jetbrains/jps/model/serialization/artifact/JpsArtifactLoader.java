package org.jetbrains.jps.model.serialization.artifact;

import com.intellij.openapi.util.JDOMUtil;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsCompositeElement;
import org.jetbrains.jps.model.JpsElementFactory;
import org.jetbrains.jps.model.JpsElementReference;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.artifact.*;
import org.jetbrains.jps.model.artifact.elements.JpsCompositePackagingElement;
import org.jetbrains.jps.model.artifact.elements.JpsPackagingElement;
import org.jetbrains.jps.model.artifact.elements.JpsPackagingElementFactory;
import org.jetbrains.jps.model.serialization.JpsModelLoaderExtension;
import org.jetbrains.jps.model.serialization.JpsModuleLoader;
import org.jetbrains.jps.service.JpsServiceManager;

import java.util.Arrays;
import java.util.List;

/**
 * @author nik
 */
public class JpsArtifactLoader {
  private static List<? extends JpsPackagingElementLoader<?>> STANDARD_LOADERS = Arrays.asList();
  
  public static void loadArtifacts(@NotNull JpsProject project, @Nullable Element componentElement) {
    JpsArtifactService service = JpsArtifactService.getInstance();
    for (Element artifactElement : JDOMUtil.getChildren(componentElement, "artifact")) {
      ArtifactState state = XmlSerializer.deserialize(artifactElement, ArtifactState.class);
      if (state == null) continue;
      JpsArtifactType artifactType = getArtifactType(state.getArtifactType());
      JpsPackagingElement rootElement = loadPackagingElement(state.getRootElement());
      if (rootElement != null) {
        JpsArtifact artifact = service.addArtifact(project, state.getName(), (JpsCompositePackagingElement)rootElement, artifactType);
        artifact.setOutputPath(state.getOutputPath());
      }
    }
  }

  @Nullable
  private static JpsPackagingElement loadPackagingElement(Element element) {
    JpsPackagingElement packagingElement = createPackagingElement(element);
    if (packagingElement instanceof JpsCompositePackagingElement) {
      for (Element childElement : JDOMUtil.getChildren(element, "element")) {
        JpsPackagingElement child = loadPackagingElement(childElement);
        if (child != null) {
          ((JpsCompositePackagingElement)packagingElement).addChild(child);
        }
      }
    }
    return packagingElement;
  }

  @Nullable
  private static JpsPackagingElement createPackagingElement(Element element) {
    String typeId = element.getAttributeValue("id");
    JpsPackagingElementFactory factory = JpsPackagingElementFactory.getInstance();
    if (typeId.equals("root")) {
      return factory.createArtifactRoot();
    }
    if (typeId.equals("directory")) {
      return factory.createDirectory(element.getAttributeValue("name"));
    }
    if (typeId.equals("archive")) {
      return factory.createArchive(element.getAttributeValue("name"));
    }
    if (typeId.equals("file-copy")) {
      return factory.createFileCopy(element.getAttributeValue("path"),
                                     element.getAttributeValue("output-file-name"));
    }
    if (typeId.equals("dir-copy")) {
      return factory.createDirectoryCopy(element.getAttributeValue("path"));
    }
    if (typeId.equals("extracted-dir")) {
      return factory.createExtractedDirectory(element.getAttributeValue("path"),
                                               element.getAttributeValue("path-in-jar"));
    }
    if (typeId.equals("library")) {
      String level = element.getAttributeValue("level");
      String libraryName = element.getAttributeValue("name");
      String moduleName = element.getAttributeValue("module-name");
      JpsElementReference<? extends JpsCompositeElement> parentReference;
      if (moduleName != null) {
        parentReference = JpsElementFactory.getInstance().createModuleReference(moduleName);
      }
      else {
        parentReference = JpsModuleLoader.createLibraryTableReference(level);
      }
      return factory.createLibraryElement(JpsElementFactory.getInstance().createLibraryReference(libraryName, parentReference));
    }
    if (typeId.equals("artifact")) {
      return factory.createArtifactOutput(JpsArtifactService.getInstance().createReference(element.getAttributeValue("artifact-name")));
    }
    JpsPackagingElementLoader<?> loader = findElementLoader(typeId);
    if (loader != null) {
      return loader.load(element);
    }
    return null;
  }

  private static JpsArtifactType getArtifactType(@Nullable String typeId) {
    if (typeId == null || "plain".equals(typeId)) {
      return DirectoryArtifactType.INSTANCE;
    }
    if (typeId.equals("jar")) {
      return JarArtifactType.INSTANCE;
    }
    for (JpsModelLoaderExtension extension : JpsServiceManager.getInstance().getExtensions(JpsModelLoaderExtension.class)) {
      JpsArtifactType type = extension.getArtifactType(typeId);
      if (type != null) {
        return type;
      }
    }
    return DirectoryArtifactType.INSTANCE;
  }

  @Nullable 
  private static JpsPackagingElementLoader<?> findElementLoader(@NotNull String typeId) {
    for (JpsPackagingElementLoader<?> loader : STANDARD_LOADERS) {
      if (loader.getTypeId().equals(typeId)) {
        return loader;
      }
    }
    for (JpsModelLoaderExtension extension : JpsServiceManager.getInstance().getExtensions(JpsModelLoaderExtension.class)) {
      for (JpsPackagingElementLoader<?> loader : extension.getPackagingElementLoaders()) {
        if (loader.getTypeId().equals(typeId)) {
          return loader;
        }
      }
    }
    return null;
  }
}
