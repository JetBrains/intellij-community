/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.jps.model.serialization.artifact;

import com.intellij.openapi.util.JDOMUtil;
import com.intellij.util.xmlb.SkipDefaultValuesSerializationFilters;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.*;
import org.jetbrains.jps.model.artifact.*;
import org.jetbrains.jps.model.artifact.elements.*;
import org.jetbrains.jps.model.library.JpsLibraryReference;
import org.jetbrains.jps.model.module.JpsModuleReference;
import org.jetbrains.jps.model.serialization.JpsModelSerializerExtension;
import org.jetbrains.jps.model.serialization.library.JpsLibraryTableSerializer;

import java.util.List;

/**
 * @author nik
 */
public class JpsArtifactSerializer {
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
  private static final SkipDefaultValuesSerializationFilters SERIALIZATION_FILTERS = new SkipDefaultValuesSerializationFilters();


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

  public static void saveArtifact(@NotNull JpsArtifact artifact, Element componentElement) {
    ArtifactState state = new ArtifactState();
    state.setName(artifact.getName());
    state.setBuildOnMake(artifact.isBuildOnMake());
    state.setOutputPath(artifact.getOutputPath());
    JpsArtifactPropertiesSerializer<?> serializer = getTypePropertiesSerializer(artifact.getArtifactType());
    doSaveArtifact(artifact, componentElement, state, serializer);
  }

  private static <P extends JpsElement> void doSaveArtifact(JpsArtifact artifact, Element componentElement, ArtifactState state,
                                                            JpsArtifactPropertiesSerializer<P> serializer) {
    state.setArtifactType(serializer.getTypeId());
    state.setRootElement(savePackagingElement(artifact.getRootElement()));
    List<ArtifactPropertiesState> propertiesList = state.getPropertiesList();
    //noinspection unchecked
    serializer.saveProperties((P)artifact.getProperties(), propertiesList);
    for (JpsModelSerializerExtension serializerExtension : JpsModelSerializerExtension.getExtensions()) {
      for (JpsArtifactExtensionSerializer<?> extensionSerializer : serializerExtension.getArtifactExtensionSerializers()) {
        JpsElement extension = artifact.getContainer().getChild(extensionSerializer.getRole());
        if (extension != null) {
          ArtifactPropertiesState propertiesState = new ArtifactPropertiesState();
          propertiesState.setId(extensionSerializer.getId());
          propertiesState.setOptions(saveExtension(extensionSerializer, extension));
          propertiesList.add(propertiesState);
        }
      }
    }
    componentElement.addContent(XmlSerializer.serialize(state, SERIALIZATION_FILTERS));
  }

  private static <E extends JpsElement> void loadExtension(JpsArtifactExtensionSerializer<E> serializer,
                                                           JpsArtifact artifact,
                                                           Element options) {
    E e = serializer.loadExtension(options);
    artifact.getContainer().setChild(serializer.getRole(), e);
  }

  private static <E extends JpsElement> Element saveExtension(JpsArtifactExtensionSerializer<?> serializer,
                                                              E extension) {
    Element optionsTag = new Element("options");
    //noinspection unchecked
    ((JpsArtifactExtensionSerializer<E>)serializer).saveExtension(extension, optionsTag);
    return optionsTag;
  }

  private static <P extends JpsPackagingElement> Element savePackagingElement(P element) {
    //noinspection unchecked
    JpsPackagingElementSerializer<P> serializer = findElementSerializer((Class<P>)element.getClass());
    Element tag = new Element(ELEMENT_TAG).setAttribute(ID_ATTRIBUTE, serializer.getTypeId());
    serializer.save(element, tag);
    if (element instanceof JpsCompositePackagingElement) {
      for (JpsPackagingElement child : ((JpsCompositePackagingElement)element).getChildren()) {
        tag.addContent(savePackagingElement(child));
      }
    }
    return tag;
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

  @NotNull
  private static <E extends JpsPackagingElement> JpsPackagingElementSerializer<E> findElementSerializer(@NotNull Class<E> elementClass) {
    for (JpsPackagingElementSerializer<?> serializer : STANDARD_SERIALIZERS) {
      if (serializer.getElementClass().isAssignableFrom(elementClass)) {
        //noinspection unchecked
        return (JpsPackagingElementSerializer<E>)serializer;
      }
    }
    for (JpsModelSerializerExtension extension : JpsModelSerializerExtension.getExtensions()) {
      for (JpsPackagingElementSerializer<?> serializer : extension.getPackagingElementSerializers()) {
        if (serializer.getElementClass().isAssignableFrom(elementClass)) {
          //noinspection unchecked
          return (JpsPackagingElementSerializer<E>)serializer;
        }
      }
    }
    throw new IllegalArgumentException("Serializer not found for " + elementClass);
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

  private static JpsArtifactPropertiesSerializer<?> getTypePropertiesSerializer(JpsArtifactType type) {
    for (JpsArtifactPropertiesSerializer serializer : STANDARD_TYPE_SERIALIZERS) {
      if (serializer.getType().equals(type)) {
        return serializer;
      }
    }
    for (JpsModelSerializerExtension extension : JpsModelSerializerExtension.getExtensions()) {
      for (JpsArtifactPropertiesSerializer serializer : extension.getArtifactTypePropertiesSerializers()) {
        if (serializer.getType().equals(type)) {
          return serializer;
        }
      }
    }
    return null;
  }

  private static class ArtifactRootElementSerializer extends JpsPackagingElementSerializer<JpsArtifactRootElement> {
    public ArtifactRootElementSerializer() {
      super("root", JpsArtifactRootElement.class);
    }

    @Override
    public JpsArtifactRootElement load(Element element) {
      return JpsPackagingElementFactory.getInstance().createArtifactRoot();
    }

    @Override
    public void save(JpsArtifactRootElement element, Element tag) {
    }
  }

  private static class DirectoryElementSerializer extends JpsPackagingElementSerializer<JpsDirectoryPackagingElement> {
    public DirectoryElementSerializer() {
      super("directory", JpsDirectoryPackagingElement.class);
    }

    @Override
    public JpsDirectoryPackagingElement load(Element element) {
      return JpsPackagingElementFactory.getInstance().createDirectory(element.getAttributeValue("name"));
    }

    @Override
    public void save(JpsDirectoryPackagingElement element, Element tag) {
      tag.setAttribute("name", element.getDirectoryName());
    }
  }

  private static class ArchiveElementSerializer extends JpsPackagingElementSerializer<JpsArchivePackagingElement> {
    public ArchiveElementSerializer() {
      super("archive", JpsArchivePackagingElement.class);
    }

    @Override
    public JpsArchivePackagingElement load(Element element) {
      return JpsPackagingElementFactory.getInstance().createArchive(element.getAttributeValue("name"));
    }

    @Override
    public void save(JpsArchivePackagingElement element, Element tag) {
      tag.setAttribute("name", element.getArchiveName());
    }
  }

  private static class FileCopyElementSerializer extends JpsPackagingElementSerializer<JpsFileCopyPackagingElement> {
    public FileCopyElementSerializer() {
      super("file-copy", JpsFileCopyPackagingElement.class);
    }

    @Override
    public JpsFileCopyPackagingElement load(Element element) {
      return JpsPackagingElementFactory.getInstance().createFileCopy(element.getAttributeValue("path"),
                                                                     element.getAttributeValue("output-file-name"));
    }

    @Override
    public void save(JpsFileCopyPackagingElement element, Element tag) {
      tag.setAttribute("path", element.getFilePath());
      String outputFileName = element.getRenamedOutputFileName();
      if (outputFileName != null) {
        tag.setAttribute("output-path-name", outputFileName);
      }
    }
  }

  private static class DirectoryCopyElementSerializer extends JpsPackagingElementSerializer<JpsDirectoryCopyPackagingElement> {
    public DirectoryCopyElementSerializer() {
      super("dir-copy", JpsDirectoryCopyPackagingElement.class);
    }

    @Override
    public JpsDirectoryCopyPackagingElement load(Element element) {
      return JpsPackagingElementFactory.getInstance().createDirectoryCopy(element.getAttributeValue("path"));
    }

    @Override
    public void save(JpsDirectoryCopyPackagingElement element, Element tag) {
      tag.setAttribute("path", element.getDirectoryPath());
    }
  }

  private static class ExtractedDirectoryElementSerializer
    extends JpsPackagingElementSerializer<JpsExtractedDirectoryPackagingElement> {
    public ExtractedDirectoryElementSerializer() {
      super("extracted-dir", JpsExtractedDirectoryPackagingElement.class);
    }

    @Override
    public JpsExtractedDirectoryPackagingElement load(Element element) {
      return JpsPackagingElementFactory.getInstance().createExtractedDirectory(element.getAttributeValue("path"),
                                                                               element.getAttributeValue("path-in-jar"));
    }

    @Override
    public void save(JpsExtractedDirectoryPackagingElement element, Element tag) {
      tag.setAttribute("path", element.getFilePath());
      tag.setAttribute("path-in-jar", element.getPathInJar());
    }
  }

  private static class LibraryFilesElementSerializer extends JpsPackagingElementSerializer<JpsLibraryFilesPackagingElement> {
    public LibraryFilesElementSerializer() {
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

    @Override
    public void save(JpsLibraryFilesPackagingElement element, Element tag) {
      JpsLibraryReference reference = element.getLibraryReference();
      JpsElementReference<? extends JpsCompositeElement> parentReference = reference.getParentReference();
      tag.setAttribute("level", JpsLibraryTableSerializer.getLevelId(parentReference));
      tag.setAttribute("name", reference.getLibraryName());
      if (parentReference instanceof JpsModuleReference) {
        tag.setAttribute("module-name", ((JpsModuleReference)parentReference).getModuleName());
      }
    }
  }

  private static class ArtifactOutputElementSerializer extends JpsPackagingElementSerializer<JpsArtifactOutputPackagingElement> {
    public ArtifactOutputElementSerializer() {
      super("artifact", JpsArtifactOutputPackagingElement.class);
    }

    @Override
    public JpsArtifactOutputPackagingElement load(Element element) {
      return JpsPackagingElementFactory.getInstance()
        .createArtifactOutput(JpsArtifactService.getInstance().createReference(element.getAttributeValue("artifact-name")));
    }

    @Override
    public void save(JpsArtifactOutputPackagingElement element, Element tag) {
      tag.setAttribute("artifact-name", element.getArtifactReference().getArtifactName());
    }
  }
}
