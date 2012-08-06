package org.jetbrains.jps.model.serialization.artifact;

import com.intellij.openapi.util.JDOMUtil;
import com.intellij.util.xmlb.SkipDefaultValuesSerializationFilters;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsCompositeElement;
import org.jetbrains.jps.model.JpsElementFactory;
import org.jetbrains.jps.model.JpsElementReference;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.artifact.*;
import org.jetbrains.jps.model.artifact.elements.*;
import org.jetbrains.jps.model.library.JpsLibraryReference;
import org.jetbrains.jps.model.module.JpsModuleReference;
import org.jetbrains.jps.model.serialization.JpsLibraryTableSerializer;
import org.jetbrains.jps.model.serialization.JpsModelSerializerExtension;

/**
 * @author nik
 */
public class JpsArtifactSerializer {
  private static JpsPackagingElementSerializer<?>[] STANDARD_SERIALIZERS = {
    new ArtifactRootElementSerializer(),
    new DirectoryElementSerializer(),
    new ArchiveElementSerializer(),
    new FileCopyElementSerializer(),
    new DirectoryCopyElementSerializer(),
    new ExtractedDirectoryElementSerializer(),
    new LibraryFilesElementSerializer(),
    new ArtifactOutputElementSerializer()
  };
  private static final JpsArtifactTypeSerializer[] STANDARD_TYPE_SERIALIZERS = {
    new JpsArtifactTypeSerializer("plain", DirectoryArtifactType.INSTANCE),
    new JpsArtifactTypeSerializer("jar", JarArtifactType.INSTANCE)
  };
  private static final String ELEMENT_TAG = "element";
  private static final String ID_ATTRIBUTE = "id";
  private static final SkipDefaultValuesSerializationFilters SERIALIZATION_FILTERS = new SkipDefaultValuesSerializationFilters();


  public static void loadArtifacts(@NotNull JpsProject project, @Nullable Element componentElement) {
    JpsArtifactService service = JpsArtifactService.getInstance();
    for (Element artifactElement : JDOMUtil.getChildren(componentElement, "artifact")) {
      ArtifactState state = XmlSerializer.deserialize(artifactElement, ArtifactState.class);
      if (state == null) continue;
      JpsArtifactType artifactType = getTypeSerializer(state.getArtifactType()).getType();
      JpsPackagingElement rootElement = loadPackagingElement(state.getRootElement());
      if (rootElement != null) {
        JpsArtifact artifact = service.addArtifact(project, state.getName(), (JpsCompositePackagingElement)rootElement, artifactType);
        artifact.setBuildOnMake(state.isBuildOnMake());
        artifact.setOutputPath(state.getOutputPath());
      }
    }
  }

  public static void saveArtifact(@NotNull JpsArtifact artifact, Element componentElement) {
    ArtifactState state = new ArtifactState();
    state.setName(artifact.getName());
    state.setBuildOnMake(artifact.isBuildOnMake());
    state.setOutputPath(artifact.getOutputPath());
    state.setArtifactType(getTypeSerializer(artifact.getArtifactType()).getTypeId());
    state.setRootElement(savePackagingElement(artifact.getRootElement()));
    componentElement.addContent(XmlSerializer.serialize(state, SERIALIZATION_FILTERS));
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

  private static JpsArtifactTypeSerializer getTypeSerializer(String typeId) {
    for (JpsArtifactTypeSerializer serializer : STANDARD_TYPE_SERIALIZERS) {
      if (serializer.getTypeId().equals(typeId)) {
        return serializer;
      }
    }
    for (JpsModelSerializerExtension extension : JpsModelSerializerExtension.getExtensions()) {
      for (JpsArtifactTypeSerializer serializer : extension.getArtifactTypeSerializers()) {
        if (serializer.getTypeId().equals(typeId)) {
          return serializer;
        }
      }
    }
    return STANDARD_TYPE_SERIALIZERS[0];
  }

  private static JpsArtifactTypeSerializer getTypeSerializer(JpsArtifactType type) {
    for (JpsArtifactTypeSerializer serializer : STANDARD_TYPE_SERIALIZERS) {
      if (serializer.getType().equals(type)) {
        return serializer;
      }
    }
    for (JpsModelSerializerExtension extension : JpsModelSerializerExtension.getExtensions()) {
      for (JpsArtifactTypeSerializer serializer : extension.getArtifactTypeSerializers()) {
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
