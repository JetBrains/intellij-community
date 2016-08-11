/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.packaging.impl.artifacts;

import com.intellij.compiler.server.BuildManager;
import com.intellij.openapi.application.*;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.ProjectLoadingErrorsNotifier;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.UnknownFeaturesCollector;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SimpleModificationTracker;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.packaging.artifacts.*;
import com.intellij.packaging.elements.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.SkipDefaultValuesSerializationFilters;
import com.intellij.util.xmlb.XmlSerializer;
import gnu.trove.THashSet;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.serialization.artifact.ArtifactManagerState;
import org.jetbrains.jps.model.serialization.artifact.ArtifactPropertiesState;
import org.jetbrains.jps.model.serialization.artifact.ArtifactState;

import java.util.*;

/**
 * @author nik
 */
@State(name = ArtifactManagerImpl.COMPONENT_NAME, storages = @Storage(value = "artifacts", stateSplitter = ArtifactManagerStateSplitter.class))
public class ArtifactManagerImpl extends ArtifactManager implements ProjectComponent, PersistentStateComponent<ArtifactManagerState> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.packaging.impl.artifacts.ArtifactManagerImpl");
  @NonNls public static final String COMPONENT_NAME = "ArtifactManager";
  @NonNls public static final String PACKAGING_ELEMENT_NAME = "element";
  @NonNls public static final String TYPE_ID_ATTRIBUTE = "id";
  private final ArtifactManagerModel myModel;
  private final Project myProject;
  private final DefaultPackagingElementResolvingContext myResolvingContext;
  private boolean myInsideCommit = false;
  private boolean myLoaded;
  private final SimpleModificationTracker myModificationTracker = new SimpleModificationTracker();
  private final Map<String, LocalFileSystem.WatchRequest> myWatchedOutputs = new HashMap<>();

  public ArtifactManagerImpl(Project project) {
    myProject = project;
    myModel = new ArtifactManagerModel();
    myResolvingContext = new DefaultPackagingElementResolvingContext(myProject);
    ((ArtifactPointerManagerImpl)ArtifactPointerManager.getInstance(project)).setArtifactManager(this);
  }

  @Override
  @NotNull
  public Artifact[] getArtifacts() {
    return myModel.getArtifacts();
  }

  @Override
  public Artifact findArtifact(@NotNull String name) {
    return myModel.findArtifact(name);
  }

  @Override
  @NotNull
  public Artifact getArtifactByOriginal(@NotNull Artifact artifact) {
    return myModel.getArtifactByOriginal(artifact);
  }

  @Override
  @NotNull
  public Artifact getOriginalArtifact(@NotNull Artifact artifact) {
    return myModel.getOriginalArtifact(artifact);
  }

  @Override
  @NotNull
  public Collection<? extends Artifact> getArtifactsByType(@NotNull ArtifactType type) {
    return myModel.getArtifactsByType(type);
  }

  @Override
  public List<? extends Artifact> getAllArtifactsIncludingInvalid() {
    return myModel.getAllArtifactsIncludingInvalid();
  }

  @Override
  public ArtifactManagerState getState() {
    final ArtifactManagerState state = new ArtifactManagerState();
    for (Artifact artifact : getAllArtifactsIncludingInvalid()) {
      final ArtifactState artifactState;
      if (artifact instanceof InvalidArtifact) {
        artifactState = ((InvalidArtifact)artifact).getState();
      }
      else {
        artifactState = new ArtifactState();
        artifactState.setBuildOnMake(artifact.isBuildOnMake());
        artifactState.setName(artifact.getName());
        artifactState.setOutputPath(artifact.getOutputPath());
        artifactState.setRootElement(serializePackagingElement(artifact.getRootElement()));
        artifactState.setArtifactType(artifact.getArtifactType().getId());
        for (ArtifactPropertiesProvider provider : artifact.getPropertiesProviders()) {
          final ArtifactPropertiesState propertiesState = serializeProperties(provider, artifact.getProperties(provider));
          if (propertiesState != null) {
            artifactState.getPropertiesList().add(propertiesState);
          }
        }
        Collections.sort(artifactState.getPropertiesList(), (o1, o2) -> o1.getId().compareTo(o2.getId()));
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
    element.setAttribute(TYPE_ID_ATTRIBUTE, packagingElement.getType().getId());
    final Object bean = packagingElement.getState();
    if (bean != null) {
      XmlSerializer.serializeInto(bean, element, new SkipDefaultValuesSerializationFilters());
    }
    if (packagingElement instanceof CompositePackagingElement) {
      for (PackagingElement<?> child : ((CompositePackagingElement<?>)packagingElement).getChildren()) {
        element.addContent(serializePackagingElement(child));
      }
    }
    return element;
  }

  private <T> PackagingElement<T> deserializeElement(Element element) throws UnknownPackagingElementTypeException {
    final String id = element.getAttributeValue(TYPE_ID_ATTRIBUTE);
    PackagingElementType<?> type = PackagingElementFactory.getInstance().findElementType(id);
    if (type == null) {
      throw new UnknownPackagingElementTypeException(id);
    }

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

  @Override
  public void loadState(ArtifactManagerState managerState) {
    List<ArtifactState> artifactStates = managerState.getArtifacts();
    final List<ArtifactImpl> artifacts = new ArrayList<>(artifactStates.size());
    if (!artifactStates.isEmpty()) {
      AccessToken token = ReadAction.start();
      try {
        for (ArtifactState state : artifactStates) {
          artifacts.add(loadArtifact(state));
        }
      }
      finally {
        token.finish();
      }
    }

    if (myLoaded) {
      final ArtifactModelImpl model = new ArtifactModelImpl(this, artifacts);
      doCommit(model);
    }
    else {
      myModel.setArtifactsList(artifacts);
      myLoaded = true;
    }
  }

  private ArtifactImpl loadArtifact(ArtifactState state) {
    ArtifactType type = ArtifactType.findById(state.getArtifactType());
    if (type == null) {
      return createInvalidArtifact(state, "Unknown artifact type: " + state.getArtifactType());
    }

    final Element element = state.getRootElement();
    final String artifactName = state.getName();
    final CompositePackagingElement<?> rootElement;
    if (element != null) {
      try {
        rootElement = (CompositePackagingElement<?>)deserializeElement(element);
      }
      catch (UnknownPackagingElementTypeException e) {
        return createInvalidArtifact(state, "Unknown element: " + e.getTypeId());
      }
    }
    else {
      rootElement = type.createRootElement(artifactName);
    }

    final ArtifactImpl artifact = new ArtifactImpl(artifactName, type, state.isBuildOnMake(), rootElement, state.getOutputPath());
    final List<ArtifactPropertiesState> propertiesList = state.getPropertiesList();
    for (ArtifactPropertiesState propertiesState : propertiesList) {
      final ArtifactPropertiesProvider provider = ArtifactPropertiesProvider.findById(propertiesState.getId());
      if (provider != null) {
        deserializeProperties(artifact.getProperties(provider), propertiesState);
      }
      else {
        return createInvalidArtifact(state, "Unknown artifact properties: " + propertiesState.getId());
      }
    }
    return artifact;
  }

  private InvalidArtifact createInvalidArtifact(ArtifactState state, String errorMessage) {
    final InvalidArtifact artifact = new InvalidArtifact(state, errorMessage);
    ProjectLoadingErrorsNotifier.getInstance(myProject).registerError(new ArtifactLoadingErrorDescription(myProject, artifact));
    UnknownFeaturesCollector.getInstance(myProject).registerUnknownFeature("com.intellij.packaging.artifacts.ArtifactType", state.getArtifactType(), "Artifact");
    return artifact;
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

  @Override
  public void disposeComponent() {
    LocalFileSystem.getInstance().removeWatchedRoots(myWatchedOutputs.values());
  }

  @Override
  @NotNull
  public String getComponentName() {
    return COMPONENT_NAME;
  }

  @Override
  public void initComponent() {
    VirtualFileManager.getInstance().addVirtualFileListener(new ArtifactVirtualFileListener(myProject, this), myProject);
    updateWatchedRoots();
  }

  private void updateWatchedRoots() {
    Set<String> pathsToRemove = new HashSet<>(myWatchedOutputs.keySet());
    Set<String> toAdd = new HashSet<>();
    for (Artifact artifact : getArtifacts()) {
      final String path = artifact.getOutputPath();
      if (path != null && path.length() > 0) {
        pathsToRemove.remove(path);
        if (!myWatchedOutputs.containsKey(path)) {
          toAdd.add(path);
        }
      }
    }

    List<LocalFileSystem.WatchRequest> requestsToRemove = new ArrayList<>();
    for (String path : pathsToRemove) {
      final LocalFileSystem.WatchRequest request = myWatchedOutputs.remove(path);
      ContainerUtil.addIfNotNull(requestsToRemove, request);
    }

    Set<LocalFileSystem.WatchRequest> newRequests = LocalFileSystem.getInstance().replaceWatchedRoots(requestsToRemove, toAdd, null);
    for (LocalFileSystem.WatchRequest request : newRequests) {
      myWatchedOutputs.put(request.getRootPath(), request);
    }
  }

  @Override
  public void projectOpened() {
  }

  @Override
  public void projectClosed() {
  }

  @Override
  public Artifact[] getSortedArtifacts() {
    return myModel.getSortedArtifacts();
  }

  @Override
  public ModifiableArtifactModel createModifiableModel() {
    return new ArtifactModelImpl(this, getArtifactsList());
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

    doCommit(artifactModel);
  }

  private void doCommit(ArtifactModelImpl artifactModel) {
    boolean hasChanges;
    LOG.assertTrue(!myInsideCommit, "Recursive commit");
    myInsideCommit = true;
    try {

      final List<ArtifactImpl> allArtifacts = artifactModel.getOriginalArtifacts();

      final Set<ArtifactImpl> removed = new THashSet<>(myModel.myArtifactsList);
      final List<ArtifactImpl> added = new ArrayList<>();
      final List<Pair<ArtifactImpl, String>> changed = new ArrayList<>();

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
      myModificationTracker.incModificationCount();
      final ArtifactListener publisher = myProject.getMessageBus().syncPublisher(TOPIC);
      hasChanges = !removed.isEmpty() || !added.isEmpty() || !changed.isEmpty();
      ProjectRootManagerEx.getInstanceEx(myProject).mergeRootsChangesDuring(() -> {
        for (ArtifactImpl artifact : removed) {
          publisher.artifactRemoved(artifact);
        }
        //it's important to send 'removed' events before 'added'. Otherwise when artifacts are reloaded from xml artifact pointers will be damaged
        for (ArtifactImpl artifact : added) {
          publisher.artifactAdded(artifact);
        }
        for (Pair<ArtifactImpl, String> pair : changed) {
          publisher.artifactChanged(pair.getFirst(), pair.getSecond());
        }
      });
    }
    finally {
      myInsideCommit = false;
    }
    updateWatchedRoots();
    if (hasChanges) {
      BuildManager.getInstance().clearState(myProject);
    }
  }

  public Project getProject() {
    return myProject;
  }

  @Override
  @NotNull
  public Artifact addArtifact(@NotNull final String name, @NotNull final ArtifactType type, final CompositePackagingElement<?> root) {
    return new WriteAction<Artifact>() {
      @Override
      protected void run(@NotNull final Result<Artifact> result) {
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
  public void addElementsToDirectory(@NotNull Artifact artifact, @NotNull String relativePath, @NotNull PackagingElement<?> element) {
    addElementsToDirectory(artifact, relativePath, Collections.singletonList(element));
  }

  @Override
  public void addElementsToDirectory(@NotNull Artifact artifact, @NotNull String relativePath,
                                     @NotNull Collection<? extends PackagingElement<?>> elements) {
    final ModifiableArtifactModel model = createModifiableModel();
    final CompositePackagingElement<?> root = model.getOrCreateModifiableArtifact(artifact).getRootElement();
    PackagingElementFactory.getInstance().getOrCreateDirectory(root, relativePath).addOrFindChildren(elements);
    new WriteAction() {
      @Override
      protected void run(@NotNull final Result result) {
        model.commit();
      }
    }.execute();
  }

  @Override
  public ModificationTracker getModificationTracker() {
    return myModificationTracker;
  }

  private static class ArtifactManagerModel extends ArtifactModelBase {
    private List<ArtifactImpl> myArtifactsList = new ArrayList<>();
    private Artifact[] mySortedArtifacts;

    public void setArtifactsList(List<ArtifactImpl> artifactsList) {
      myArtifactsList = artifactsList;
      artifactsChanged();
    }

    @Override
    protected void artifactsChanged() {
      super.artifactsChanged();
      mySortedArtifacts = null;
    }

    @Override
    protected List<? extends Artifact> getArtifactsList() {
      return myArtifactsList;
    }

    public Artifact[] getSortedArtifacts() {
      if (mySortedArtifacts == null) {
        mySortedArtifacts = getArtifacts().clone();
        Arrays.sort(mySortedArtifacts, ARTIFACT_COMPARATOR);
      }
      return mySortedArtifacts;
    }
  }

}
