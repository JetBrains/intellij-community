package com.intellij.packaging.impl.artifacts;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.packaging.artifacts.*;
import com.intellij.packaging.elements.*;
import com.intellij.util.xmlb.SkipDefaultValuesSerializationFilters;
import com.intellij.util.xmlb.XmlSerializer;
import gnu.trove.THashSet;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * @author nik
 */
@State(
    name = ArtifactManagerImpl.COMPONENT_NAME,
    storages = {
        @Storage(id = "other", file = "$PROJECT_FILE$"),
        @Storage(id = "dir", file = "$PROJECT_CONFIG_DIR$/artifacts/", scheme = StorageScheme.DIRECTORY_BASED, stateSplitter = ArtifactManagerStateSplitter.class)
    }
)
public class ArtifactManagerImpl extends ArtifactManager implements ProjectComponent, PersistentStateComponent<ArtifactManagerState> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.packaging.impl.artifacts.ArtifactManagerImpl");
  @NonNls public static final String COMPONENT_NAME = "ArtifactManager";
  private final ArtifactManagerModel myModel = new ArtifactManagerModel();
  private final Project myProject;
  private final DefaultPackagingElementResolvingContext myResolvingContext;
  private boolean myInsideCommit = false;
  @NonNls private static final String PACKAGING_ELEMENT_NAME = "element";

  public ArtifactManagerImpl(Project project) {
    myProject = project;
    myResolvingContext = new DefaultPackagingElementResolvingContext(myProject);
  }

  @NotNull
  public Artifact[] getArtifacts() {
    return myModel.getArtifacts();
  }

  public Artifact findArtifact(@NotNull String name) {
    return myModel.findArtifact(name);
  }

  @NotNull
  public Artifact getArtifactByOriginal(@NotNull Artifact artifact) {
    return myModel.getArtifactByOriginal(artifact);
  }

  public Collection<? extends Artifact> getArtifactsByType(@NotNull ArtifactType type) {
    return myModel.getArtifactsByType(type);
  }

  public ArtifactManagerState getState() {
    final ArtifactManagerState state = new ArtifactManagerState();
    for (Artifact artifact : getArtifacts()) {
      final ArtifactState artifactState = new ArtifactState();
      artifactState.setBuildOnMake(artifact.isBuildOnMake());
      artifactState.setName(artifact.getName());
      artifactState.setOutputPath(artifact.getOutputPath());
      artifactState.setRootElement(serializePackagingElement(artifact.getRootElement()));
      artifactState.setArtifactType(artifact.getArtifactType().getId());
      artifactState.setClearOutputOnRebuild(artifact.isClearOutputDirectoryOnRebuild());
      for (ArtifactPropertiesProvider provider : artifact.getPropertiesProviders()) {
        final ArtifactPropertiesState propertiesState = serializeProperties(provider, artifact.getProperties(provider));
        if (propertiesState != null) {
          artifactState.getPropertiesList().add(propertiesState);
        }
      }
      state.getArtifacts().add(artifactState);
    }
    return state;
  }

  @Nullable
  private static <S> ArtifactPropertiesState serializeProperties(ArtifactPropertiesProvider provider, ArtifactProperties<S> properties) {
    final ArtifactPropertiesState state = new ArtifactPropertiesState();
    state.setId(provider.getId());
    final Element options = new Element("options");
    XmlSerializer.serializeInto(properties.getState(), options, new SkipDefaultValuesSerializationFilters());
    if (options.getContent().isEmpty() && options.getAttributes().isEmpty()) return null;
    state.setOptions(options);
    return state;
  }

  private static Element serializePackagingElement(PackagingElement<?> packagingElement) {
    Element element = new Element(PACKAGING_ELEMENT_NAME);
    element.setAttribute("id", packagingElement.getType().getId());
    final Object bean = packagingElement.getState();
    if (bean != null) {
      XmlSerializer.serializeInto(bean, element);
    }
    if (packagingElement instanceof CompositePackagingElement) {
      for (PackagingElement<?> child : ((CompositePackagingElement<?>)packagingElement).getChildren()) {
        element.addContent(serializePackagingElement(child));
      }
    }
    return element;
  }

  private <T> PackagingElement<T> deserializeElement(Element element) {
    final String id = element.getAttributeValue("id");
    PackagingElementType<?> type = PackagingElementFactory.getInstance().findElementType(id);
    PackagingElement<T> packagingElement = (PackagingElement<T>)type.createEmpty(myProject);
    T state = packagingElement.getState();
    if (state != null) {
      XmlSerializer.deserializeInto(state, element);
      packagingElement.loadState(state);
    }
    final List children = element.getChildren(PACKAGING_ELEMENT_NAME);
    //noinspection unchecked
    for (Element child : (List<? extends Element>)children) {
      ((CompositePackagingElement<?>)packagingElement).addOrFindChild(deserializeElement(child));
    }
    return packagingElement;
  }

  public void loadState(ArtifactManagerState managerState) {
    final List<ArtifactImpl> artifacts = new ArrayList<ArtifactImpl>();
    for (ArtifactState state : managerState.getArtifacts()) {
      final Element element = state.getRootElement();
      ArtifactType type = ArtifactType.findById(state.getArtifactType());
      if (type == null) {
        LOG.info("Unknown artifact type: " + state.getArtifactType());
        continue;
      }
      
      final CompositePackagingElement<?> rootElement;
      if (element != null) {
        rootElement = (CompositePackagingElement<?>)deserializeElement(element);
      }
      else {
        rootElement = type.createRootElement();
      }

      final ArtifactImpl artifact = new ArtifactImpl(state.getName(), type, state.isBuildOnMake(), rootElement, state.getOutputPath(),
                                                     state.isClearOutputOnRebuild());
      final List<ArtifactPropertiesState> propertiesList = state.getPropertiesList();
      for (ArtifactPropertiesState propertiesState : propertiesList) {
        final ArtifactPropertiesProvider provider = ArtifactPropertiesProvider.findById(propertiesState.getId());
        if (provider != null) {
          deserializeProperties(artifact.getProperties(provider), propertiesState);
        }
      }
      artifacts.add(artifact);
    }
    myModel.setArtifactsList(artifacts);
  }

  private static <S> void deserializeProperties(ArtifactProperties<S> artifactProperties, ArtifactPropertiesState propertiesState) {
    final Element options = propertiesState.getOptions();
    if (artifactProperties == null || options == null) {
      return;
    }
    final S state = artifactProperties.getState();
    if (state != null) {
      XmlSerializer.deserializeInto(state, options);
      artifactProperties.loadState(state);
    }
  }

  public void disposeComponent() {
  }

  @NotNull
  public String getComponentName() {
    return COMPONENT_NAME;
  }

  public void initComponent() {
  }

  public void projectOpened() {
  }

  public void projectClosed() {
  }

  @Override
  public ModifiableArtifactModel createModifiableModel() {
    ((ArtifactPointerManagerImpl)ArtifactPointerManager.getInstance(myProject)).updateAllPointers();
    final ArtifactModelImpl model = new ArtifactModelImpl(this);
    model.addArtifacts(getArtifactsList());
    return model;
  }

  @Override
  public PackagingElementResolvingContext getResolvingContext() {
    return myResolvingContext;
  }

  public List<ArtifactImpl> getArtifactsList() {
    return myModel.myArtifactsList;
  }

  public void commit(ArtifactModelImpl artifactModel) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    LOG.assertTrue(!myInsideCommit, "Recursive commit");

    myInsideCommit = true;
    try {

      final List<ArtifactImpl> allArtifacts = artifactModel.getOriginalArtifacts();

      Set<ArtifactImpl> removed = new THashSet<ArtifactImpl>(myModel.myArtifactsList);
      List<ArtifactImpl> added = new ArrayList<ArtifactImpl>();
      List<Pair<ArtifactImpl, String>> changed = new ArrayList<Pair<ArtifactImpl, String>>();

      for (ArtifactImpl artifact : allArtifacts) {
        final boolean isAdded = !removed.remove(artifact);
        final ArtifactImpl modifiableCopy = artifactModel.getModifiableCopy(artifact);
        if (isAdded) {
          added.add(artifact);
        }
        else if (modifiableCopy != null && !modifiableCopy.equals(artifact)) {
          final String oldName = artifact.getName();
          artifact.copyFrom(modifiableCopy);
          changed.add(Pair.create(artifact, oldName));
        }
      }

      myModel.setArtifactsList(allArtifacts);
      final ArtifactListener publisher = myProject.getMessageBus().syncPublisher(TOPIC);
      for (ArtifactImpl artifact : added) {
        publisher.artifactAdded(artifact);
      }
      for (ArtifactImpl artifact : removed) {
        publisher.artifactRemoved(artifact);
      }
      for (Pair<ArtifactImpl, String> pair : changed) {
        publisher.artifactChanged(pair.getFirst(), pair.getSecond());
      }
    }
    finally {
      myInsideCommit = false;
    }
  }

  public Project getProject() {
    return myProject;
  }

  @NotNull
  public Artifact addArtifact(@NotNull final String name, @NotNull final ArtifactType type, final CompositePackagingElement<?> root) {
    return new WriteAction<Artifact>() {
      protected void run(final Result<Artifact> result) {
        final ModifiableArtifactModel model = createModifiableModel();
        final ModifiableArtifact artifact = model.addArtifact(name, type);
        if (root != null) {
          artifact.setRootElement(root);
        }
        model.commit();
        result.setResult(artifact);
      }
    }.execute().getResultObject();
  }

  @Override
  public void addElementsToDirectory(@NotNull Artifact artifact, @NotNull String relativePath,
                                     @NotNull Collection<? extends PackagingElement<?>> elements) {
    final ModifiableArtifactModel model = createModifiableModel();
    final CompositePackagingElement<?> root = model.getOrCreateModifiableArtifact(artifact).getRootElement();
    PackagingElementFactory.getInstance().getOrCreateDirectory(root, relativePath).addOrFindChildren(elements);
    new WriteAction() {
      protected void run(final Result result) {
        model.commit();
      }
    }.execute();
  }

  private static class ArtifactManagerModel extends ArtifactModelBase {
    private List<ArtifactImpl> myArtifactsList = new ArrayList<ArtifactImpl>();

    public void setArtifactsList(List<ArtifactImpl> artifactsList) {
      myArtifactsList = artifactsList;
      artifactsChanged();
    }

    protected List<? extends Artifact> getArtifactsList() {
      return myArtifactsList;
    }
  }

}
