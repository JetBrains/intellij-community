// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.model.serialization.artifact;

import com.intellij.openapi.util.JDOMUtil;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.*;
import org.jetbrains.jps.model.artifact.DirectoryArtifactType;
import org.jetbrains.jps.model.artifact.JarArtifactType;
import org.jetbrains.jps.model.artifact.JpsArtifact;
import org.jetbrains.jps.model.artifact.JpsArtifactService;
import org.jetbrains.jps.model.artifact.elements.*;
import org.jetbrains.jps.model.serialization.JpsModelSerializerExtension;
import org.jetbrains.jps.model.serialization.library.JpsLibraryTableSerializer;

import java.util.List;

public final class JpsArtifactSerializer {
  private static final JpsPackagingElementSerializer<?>[] STANDARD_SERIALIZERS = {
    new ArtifactRootElementSerializer(),
    new DirectoryElementSerializer(),
    new ArchiveElementSerializer(),
    new FileCopyElementSerializer(),
    new DirectoryCopyElementSerializer(),
    new ExtractedDirectoryElementSerializer(),
    new LibraryFilesElementSerializer(),
    new ArtifactOutputElementSerializer()
  };
  private static final JpsArtifactPropertiesSerializer<?>[] STANDARD_TYPE_SERIALIZERS = {
    new JpsArtifactDummyPropertiesSerializer("plain", DirectoryArtifactType.INSTANCE),
    new JpsArtifactDummyPropertiesSerializer("jar", JarArtifactType.INSTANCE)
  };
  private static final String ELEMENT_TAG = "element";
  private static final String ID_ATTRIBUTE = "id";


  public static void loadArtifacts(@NotNull JpsProject project, @Nullable Element componentElement) {
    JpsArtifactService service = JpsArtifactService.getInstance();
    for (Element artifactElement : JDOMUtil.getChildren(componentElement, "artifact")) {
      ArtifactState state = XmlSerializer.deserialize(artifactElement, ArtifactState.class);
      JpsArtifactPropertiesSerializer<?> serializer = getTypePropertiesSerializer(state.getArtifactType());
      loadArtifact(project, service, state, serializer);
    }
  }

  private static <P extends JpsElement> void loadArtifact(JpsProject project, JpsArtifactService service, ArtifactState state, JpsArtifactPropertiesSerializer<P> serializer) {
    JpsPackagingElement rootElement = loadPackagingElement(state.getRootElement());
    if (rootElement != null) {
      List<ArtifactPropertiesState> propertiesList = state.getPropertiesList();
      JpsArtifact artifact = service.addArtifact(project, state.getName(), (JpsCompositePackagingElement)rootElement,
                                                 serializer.getType(), serializer.loadProperties(propertiesList));
      artifact.setBuildOnMake(state.isBuildOnMake());
      artifact.setOutputPath(state.getOutputPath());
      for (ArtifactPropertiesState propertiesState : propertiesList) {
        JpsArtifactExtensionSerializer<?> extensionSerializer = getExtensionSerializer(propertiesState.getId());
        if (extensionSerializer != null) {
          loadExtension(extensionSerializer, artifact, propertiesState.getOptions());
        }
      }
    }
  }

  private static <E extends JpsElement> void loadExtension(JpsArtifactExtensionSerializer<E> serializer,
                                                           JpsArtifact artifact,
                                                           Element options) {
    E e = serializer.loadExtension(options);
    artifact.getContainer().setChild(serializer.getRole(), e);
  }

  @Nullable
  private static JpsPackagingElement loadPackagingElement(Element element) {
    JpsPackagingElement packagingElement = createPackagingElement(element);
    if (packagingElement instanceof JpsCompositePackagingElement) {
      for (Element childElement : JDOMUtil.getChildren(element, ELEMENT_TAG)) {
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
    String typeId = element.getAttributeValue(ID_ATTRIBUTE);
    if (typeId == null) return null;
    JpsPackagingElementSerializer<?> serializer = findElementSerializer(typeId);
    if (serializer != null) {
      return serializer.load(element);
    }
    return null;
  }

  @Nullable
  private static JpsPackagingElementSerializer<?> findElementSerializer(@NotNull String typeId) {
    for (JpsPackagingElementSerializer<?> serializer : STANDARD_SERIALIZERS) {
      if (serializer.getTypeId().equals(typeId)) {
        return serializer;
      }
    }
    for (JpsModelSerializerExtension extension : JpsModelSerializerExtension.getExtensions()) {
      for (JpsPackagingElementSerializer<?> serializer : extension.getPackagingElementSerializers()) {
        if (serializer.getTypeId().equals(typeId)) {
          return serializer;
        }
      }
    }
    return null;
  }

  @Nullable
  private static JpsArtifactExtensionSerializer<?> getExtensionSerializer(String id) {
    for (JpsModelSerializerExtension extension : JpsModelSerializerExtension.getExtensions()) {
      for (JpsArtifactExtensionSerializer<?> serializer : extension.getArtifactExtensionSerializers()) {
        if (serializer.getId().equals(id)) {
          return serializer;
        }
      }
    }
    return null;
  }

  private static JpsArtifactPropertiesSerializer<?> getTypePropertiesSerializer(String typeId) {
    for (JpsArtifactPropertiesSerializer serializer : STANDARD_TYPE_SERIALIZERS) {
      if (serializer.getTypeId().equals(typeId)) {
        return serializer;
      }
    }
    for (JpsModelSerializerExtension extension : JpsModelSerializerExtension.getExtensions()) {
      for (JpsArtifactPropertiesSerializer serializer : extension.getArtifactTypePropertiesSerializers()) {
        if (serializer.getTypeId().equals(typeId)) {
          return serializer;
        }
      }
    }
    return STANDARD_TYPE_SERIALIZERS[0];
  }

  private static class ArtifactRootElementSerializer extends JpsPackagingElementSerializer<JpsArtifactRootElement> {
    ArtifactRootElementSerializer() {
      super("root", JpsArtifactRootElement.class);
    }

    @Override
    public JpsArtifactRootElement load(Element element) {
      return JpsPackagingElementFactory.getInstance().createArtifactRoot();
    }
  }

  private static class DirectoryElementSerializer extends JpsPackagingElementSerializer<JpsDirectoryPackagingElement> {
    DirectoryElementSerializer() {
      super("directory", JpsDirectoryPackagingElement.class);
    }

    @Override
    public JpsDirectoryPackagingElement load(Element element) {
      return JpsPackagingElementFactory.getInstance().createDirectory(element.getAttributeValue("name"));
    }
  }

  private static class ArchiveElementSerializer extends JpsPackagingElementSerializer<JpsArchivePackagingElement> {
    ArchiveElementSerializer() {
      super("archive", JpsArchivePackagingElement.class);
    }

    @Override
    public JpsArchivePackagingElement load(Element element) {
      return JpsPackagingElementFactory.getInstance().createArchive(element.getAttributeValue("name"));
    }
  }

  private static class FileCopyElementSerializer extends JpsPackagingElementSerializer<JpsFileCopyPackagingElement> {
    FileCopyElementSerializer() {
      super("file-copy", JpsFileCopyPackagingElement.class);
    }

    @Override
    public JpsFileCopyPackagingElement load(Element element) {
      return JpsPackagingElementFactory.getInstance().createFileCopy(element.getAttributeValue("path"),
                                                                     element.getAttributeValue("output-file-name"));
    }
  }

  private static class DirectoryCopyElementSerializer extends JpsPackagingElementSerializer<JpsDirectoryCopyPackagingElement> {
    DirectoryCopyElementSerializer() {
      super("dir-copy", JpsDirectoryCopyPackagingElement.class);
    }

    @Override
    public JpsDirectoryCopyPackagingElement load(Element element) {
      return JpsPackagingElementFactory.getInstance().createDirectoryCopy(element.getAttributeValue("path"));
    }
  }

  private static class ExtractedDirectoryElementSerializer
    extends JpsPackagingElementSerializer<JpsExtractedDirectoryPackagingElement> {
    ExtractedDirectoryElementSerializer() {
      super("extracted-dir", JpsExtractedDirectoryPackagingElement.class);
    }

    @Override
    public JpsExtractedDirectoryPackagingElement load(Element element) {
      return JpsPackagingElementFactory.getInstance().createExtractedDirectory(element.getAttributeValue("path"),
                                                                               element.getAttributeValue("path-in-jar"));
    }
  }

  private static class LibraryFilesElementSerializer extends JpsPackagingElementSerializer<JpsLibraryFilesPackagingElement> {
    LibraryFilesElementSerializer() {
      super("library", JpsLibraryFilesPackagingElement.class);
    }

    @Override
    public JpsLibraryFilesPackagingElement load(Element element) {
      String level = element.getAttributeValue("level");
      String libraryName = element.getAttributeValue("name");
      String moduleName = element.getAttributeValue("module-name");
      JpsElementReference<? extends JpsCompositeElement> parentReference;
      if (moduleName != null) {
        parentReference = JpsElementFactory.getInstance().createModuleReference(moduleName);
      }
      else {
        parentReference = JpsLibraryTableSerializer.createLibraryTableReference(level);
      }
      return JpsPackagingElementFactory.getInstance()
        .createLibraryElement(JpsElementFactory.getInstance().createLibraryReference(libraryName, parentReference));
    }
  }

  private static class ArtifactOutputElementSerializer extends JpsPackagingElementSerializer<JpsArtifactOutputPackagingElement> {
    ArtifactOutputElementSerializer() {
      super("artifact", JpsArtifactOutputPackagingElement.class);
    }

    @Override
    public JpsArtifactOutputPackagingElement load(Element element) {
      return JpsPackagingElementFactory.getInstance()
        .createArtifactOutput(JpsArtifactService.getInstance().createReference(element.getAttributeValue("artifact-name")));
    }
  }
}
