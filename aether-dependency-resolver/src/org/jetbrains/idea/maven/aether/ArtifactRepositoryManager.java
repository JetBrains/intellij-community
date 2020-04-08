// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.aether;

import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.DependencyVisitor;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.resolution.*;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transfer.TransferCancelledException;
import org.eclipse.aether.transfer.TransferEvent;
import org.eclipse.aether.transfer.TransferListener;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.eclipse.aether.util.artifact.DelegatingArtifact;
import org.eclipse.aether.util.artifact.JavaScopes;
import org.eclipse.aether.util.filter.DependencyFilterUtils;
import org.eclipse.aether.util.graph.visitor.FilteringDependencyVisitor;
import org.eclipse.aether.util.graph.visitor.TreeDependencyVisitor;
import org.eclipse.aether.util.version.GenericVersionScheme;
import org.eclipse.aether.version.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 *
 * Aether-based repository manager and dependency resolver using maven implementation of this functionality.
 *
 * instance of this component should be managed by the code which requires dependency resolution functionality
 * all necessary params like path to local repo should be passed in constructor
 *
 */
public class ArtifactRepositoryManager {
  private static final VersionScheme ourVersioning = new GenericVersionScheme();
  private static final JreProxySelector ourProxySelector = new JreProxySelector();
  private static final Logger LOG = LoggerFactory.getLogger(ArtifactRepositoryManager.class);
  private final DefaultRepositorySystemSession mySession;

  private static final RemoteRepository MAVEN_CENTRAL_REPOSITORY = createRemoteRepository(
    "central", "https://repo1.maven.org/maven2/"
  );
  private static final RemoteRepository JBOSS_COMMUNITY_REPOSITORY = createRemoteRepository(
    "jboss.community", "https://repository.jboss.org/nexus/content/repositories/public/"
  );

  private static final RepositorySystem ourSystem;
  static {
    final DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
    locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
    locator.addService(TransporterFactory.class, FileTransporterFactory.class);
    locator.addService(TransporterFactory.class, HttpTransporterFactory.class);
    locator.setErrorHandler(new DefaultServiceLocator.ErrorHandler() {
      @Override
      public void serviceCreationFailed(Class<?> type, Class<?> impl, Throwable exception) {
        if (exception != null) {
          throw new RuntimeException(exception);
        }
      }
    });
    ourSystem = locator.getService(RepositorySystem.class);
  }

  private final List<RemoteRepository> myRemoteRepositories = new ArrayList<>();

  public ArtifactRepositoryManager(@NotNull File localRepositoryPath) {
    this(localRepositoryPath, ProgressConsumer.DEAF);
  }

  public ArtifactRepositoryManager(@NotNull File localRepositoryPath, @NotNull final ProgressConsumer progressConsumer) {
    // recreate remote repository objects to ensure the latest proxy settings are used
    this(localRepositoryPath, Arrays.asList(createRemoteRepository(MAVEN_CENTRAL_REPOSITORY), createRemoteRepository(JBOSS_COMMUNITY_REPOSITORY)), progressConsumer);
  }

  public ArtifactRepositoryManager(@NotNull File localRepositoryPath, List<RemoteRepository> remoteRepositories, @NotNull final ProgressConsumer progressConsumer) {
    myRemoteRepositories.addAll(remoteRepositories);
    final DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
    if (progressConsumer != ProgressConsumer.DEAF) {
      session.setTransferListener((TransferListener)Proxy
        .newProxyInstance(session.getClass().getClassLoader(), new Class[]{TransferListener.class}, new InvocationHandler() {
          private final EnumSet<TransferEvent.EventType> checkCancelEvents = EnumSet.of(TransferEvent.EventType.INITIATED, TransferEvent.EventType.STARTED, TransferEvent.EventType.PROGRESSED);
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
          final Object event = args[0];
          if (event instanceof TransferEvent) {
            final TransferEvent.EventType type = ((TransferEvent)event).getType();
            if (checkCancelEvents.contains(type) && progressConsumer.isCanceled()) {
              throw new TransferCancelledException();
            }
            progressConsumer.consume(event.toString());
            //if (type != TransferEvent.EventType.PROGRESSED) {
            //  progressConsumer.consume(event.toString());
            //}
          }
          return null;
        }
      }));
    }
    // setup session here

    session.setLocalRepositoryManager(ourSystem.newLocalRepositoryManager(session, new LocalRepository(localRepositoryPath)));
    session.setProxySelector(ourProxySelector);
    session.setReadOnly();
    mySession = session;
  }

  /**
   * Returns list of classes corresponding to classpath entries for this this module.
   */
  @SuppressWarnings("UnnecessaryFullyQualifiedName")
  public static List<Class<?>> getClassesFromDependencies() {
    return Arrays.asList(
      org.jetbrains.idea.maven.aether.ArtifactRepositoryManager.class, //this module
      org.apache.maven.repository.internal.VersionsMetadataGeneratorFactory.class, //maven-aether-provider
      org.apache.maven.artifact.Artifact.class, //maven-artifact
      org.apache.commons.lang3.StringUtils.class, //commons-lang3
      org.codehaus.plexus.util.Base64.class, //plexus-utils
      org.apache.maven.building.Problem.class, //maven-builder-support
      org.apache.maven.model.Model.class, //maven-model
      org.apache.maven.model.building.ModelBuilder.class, //maven-model-builder
      org.apache.maven.artifact.repository.metadata.Metadata.class, //maven-repository-metadata
      org.codehaus.plexus.component.annotations.Component.class, //plexus-component-annotations
      org.codehaus.plexus.interpolation.Interpolator.class, //plexus-interpolation
      org.eclipse.aether.RepositorySystem.class, //aether-api
      org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory.class, //aether-connector-basic
      org.eclipse.aether.spi.connector.RepositoryConnector.class, //aether-spi
      org.eclipse.aether.util.StringUtils.class, //aether-util
      org.eclipse.aether.impl.ArtifactResolver.class, //aether-impl
      org.eclipse.aether.transport.file.FileTransporterFactory.class, //aether-transport-file
      org.eclipse.aether.transport.http.HttpTransporterFactory.class, //aether-transport-http
      com.google.common.base.Predicate.class, //guava
      org.apache.http.HttpConnection.class, //httpcore
      org.apache.http.client.HttpClient.class, //httpclient
      org.apache.commons.logging.LogFactory.class, // commons-logging
      org.slf4j.Marker.class // slf4j
    );
  }

  public Collection<File> resolveDependency(String groupId, String artifactId, String version, boolean includeTransitiveDependencies,
                                            List<String> excludedDependencies) throws Exception {
    final List<File> files = new ArrayList<>();
    for (Artifact artifact : resolveDependencyAsArtifact(groupId, artifactId, version, EnumSet.of(ArtifactKind.ARTIFACT), includeTransitiveDependencies,
                                                         excludedDependencies)) {
      files.add(artifact.getFile());
    }
    return files;
  }

  @Nullable
  public ArtifactDependencyNode collectDependencies(String groupId, String artifactId, String versionConstraint) throws Exception {
    Set<VersionConstraint> constraints = Collections.singleton(asVersionConstraint(versionConstraint));
    CollectRequest collectRequest = createCollectRequest(groupId, artifactId, constraints, EnumSet.of(ArtifactKind.ARTIFACT));
    ArtifactDependencyTreeBuilder builder = new ArtifactDependencyTreeBuilder();
    DependencyNode root = ourSystem.collectDependencies(mySession, collectRequest).getRoot();
    if (root.getArtifact() == null && root.getChildren().size() == 1) {
      root = root.getChildren().get(0);
    }
    root.accept(new TreeDependencyVisitor(new FilteringDependencyVisitor(builder, createScopeFilter())));
    return builder.getRoot();
  }

  @NotNull
  public Collection<Artifact> resolveDependencyAsArtifact(String groupId, String artifactId, String versionConstraint,
                                                          Set<ArtifactKind> artifactKinds, boolean includeTransitiveDependencies,
                                                          List<String> excludedDependencies) throws Exception {
    final List<Artifact> artifacts = new ArrayList<>();
    final VersionConstraint originalConstraints = asVersionConstraint(versionConstraint);
    for (ArtifactKind kind : artifactKinds) {
      // RepositorySystem.resolveDependencies() ignores classifiers, so we need to set classifiers explicitly for discovered dependencies.
      // Because of that we have to first discover deps and then resolve corresponding artifacts
      try {
        final List<ArtifactRequest> requests;
        final Set<VersionConstraint> constraints;
        if (kind == ArtifactKind.ANNOTATIONS) {
          constraints = relaxForAnnotations(originalConstraints);
        } else {
          constraints = Collections.singleton(originalConstraints);
        }
        if (includeTransitiveDependencies) {
          final CollectResult collectResult = ourSystem.collectDependencies(
            mySession, createCollectRequest(groupId, artifactId, constraints, EnumSet.of(kind))
          );
          final ArtifactRequestBuilder builder = new ArtifactRequestBuilder(kind);
          DependencyFilter filter = createScopeFilter();
          if (!excludedDependencies.isEmpty()) {
            filter = DependencyFilterUtils.andFilter(filter, new ExcludeDependenciesFilter(excludedDependencies));
          }
          collectResult.getRoot().accept(new TreeDependencyVisitor(new FilteringDependencyVisitor(builder, filter)));
          requests = builder.getRequests();
        }
        else {
          requests = new ArrayList<>();
          for (Artifact artifact : toArtifacts(groupId, artifactId, constraints, Collections.singleton(kind))) {
            if (ourVersioning.parseVersionConstraint(artifact.getVersion()).getRange() != null) {
              final VersionRangeRequest versionRangeRequest = new VersionRangeRequest(artifact, Collections.unmodifiableList(myRemoteRepositories), null);
              final VersionRangeResult result = ourSystem.resolveVersionRange(mySession, versionRangeRequest);
              if (!result.getVersions().isEmpty()) {
                Artifact newArtifact = artifact.setVersion(result.getHighestVersion().toString());
                requests.add(new ArtifactRequest(newArtifact, Collections.unmodifiableList(myRemoteRepositories), null));
              }
            }
            else {
              requests.add(new ArtifactRequest(artifact, Collections.unmodifiableList(myRemoteRepositories), null));
            }
          }
        }

        if (!requests.isEmpty()) {
          try {
            for (ArtifactResult result : ourSystem.resolveArtifacts(mySession, requests)) {
              artifacts.add(result.getArtifact());
            }
          }
          catch (ArtifactResolutionException e) {
            if (kind != ArtifactKind.ARTIFACT) {
              // for sources and javadocs try to process requests one-by-one and fetch at least something
              if (requests.size() > 1) {
                for (ArtifactRequest request : requests) {
                  try {
                    final ArtifactResult result = ourSystem.resolveArtifact(mySession, request);
                    artifacts.add(result.getArtifact());
                  }
                  catch (ArtifactResolutionException ignored) {
                  }
                }
              }
            }
            else {
              // for ArtifactKind.ARTIFACT should fail if at least one request in this group fails
              throw e;
            }
          }
        }
      }
      catch (DependencyCollectionException e) {
        if (kind == ArtifactKind.ARTIFACT) {
          throw e;
        }
      }
    }
    return artifacts;
  }

  /**
   * Modify version constraint to look for applicable annotations artifact.
   *
   * Annotations artifact for a given library is matched by Group Id, Artifact Id
   * and classifier "annotations". Annotations version is selected using following rules:
   * <ul>
   *   <li>it is larger or equal to major component of library version (or lower constraint bound).
   *   E.g., annotations artifact ver 3.1 is applicable to library ver 3.6.5 (3.1 > 3.0)</li>
   *   <li>it is smaller or equal to library version with suffix (-an10000).
   *   E.g., annotations artifact ver 3.2-an3 is applicable to library ver 3.2</li>
   * </ul>
   * This allows to re-use existing annotations artifacts across different library versions
   * @param constraint - version or range constraint of original library
   * @return resulting relaxed constraint to select annotations artifact.
   */
  private static Set<VersionConstraint> relaxForAnnotations(VersionConstraint constraint) {
    String annotationsConstraint = constraint.toString();

    final Version version = constraint.getVersion();
    if (version != null) {
      final String major = version.toString().split("[.\\-_]")[0];
      annotationsConstraint = "[" + major + ", " + version.toString() + "-an10000]";
    }

    final VersionRange range = constraint.getRange();
    if (range != null) {
      final String majorLower = range.getLowerBound().getVersion().toString().split("[.\\-_]")[0];

      String upper = range.getUpperBound().isInclusive()
                     ? range.getUpperBound().toString() + "-an10000]"
                     : range.getUpperBound().toString() + ")";
      annotationsConstraint = "[" + majorLower + ", " + upper;
    }

    try {
      return Collections.singleton(ourVersioning.parseVersionConstraint(annotationsConstraint));
    } catch (InvalidVersionSpecificationException e) {
      LOG.info("Failed to parse version constraint " + annotationsConstraint, e);
    }

    return Collections.singleton(constraint);
  }

  @NotNull
  private static DependencyFilter createScopeFilter() {
    return DependencyFilterUtils.classpathFilter(JavaScopes.COMPILE, JavaScopes.RUNTIME);
  }

  /**
   * Gets the versions (in ascending order) that matched the requested range.
   */
  @NotNull
  public List<Version> getAvailableVersions(String groupId, String artifactId, String versionConstraint, final ArtifactKind artifactKind) throws Exception {
    final VersionRangeResult result = ourSystem.resolveVersionRange(
      mySession, createVersionRangeRequest(groupId, artifactId, asVersionConstraint(versionConstraint), artifactKind)
    );
    return result.getVersions();
  }

  public static RemoteRepository createRemoteRepository(final String id, final String url) {
    // for maven repos repository type should be 'default'
    return new RemoteRepository.Builder(id, "default", url).setProxy(ourProxySelector.getProxy(url)).build();
  }

  public static RemoteRepository createRemoteRepository(RemoteRepository prototype) {
    final String url = prototype.getUrl();
    return new RemoteRepository.Builder(prototype.getId(), prototype.getContentType(), url).setProxy(ourProxySelector.getProxy(url)).build();
  }

  private CollectRequest createCollectRequest(String groupId, String artifactId, Collection<VersionConstraint> versions, final Set<ArtifactKind> kinds) {
    final CollectRequest request = new CollectRequest();
    for (Artifact artifact : toArtifacts(groupId, artifactId, versions, kinds)) {
      request.addDependency(new Dependency(artifact, JavaScopes.COMPILE));
    }
    return request.setRepositories(Collections.unmodifiableList(myRemoteRepositories));
  }

  private VersionRangeRequest createVersionRangeRequest(String groupId, String artifactId, VersionConstraint versioning, final ArtifactKind artifactKind) {
    final VersionRangeRequest request = new VersionRangeRequest();
    for (Artifact artifact : toArtifacts(groupId, artifactId, Collections.singleton(versioning), EnumSet.of(artifactKind))) {
      request.setArtifact(artifact); // will be at most 1 artifact
    }
    List<RemoteRepository> repositories = new ArrayList<>(myRemoteRepositories.size());
    for (RemoteRepository repository : myRemoteRepositories) {
      RepositoryPolicy policy = new RepositoryPolicy(true, RepositoryPolicy.UPDATE_POLICY_ALWAYS, RepositoryPolicy.CHECKSUM_POLICY_WARN);
      repositories.add(new RemoteRepository.Builder(repository).setPolicy(policy).build());
    }
    return request.setRepositories(repositories);
  }

  public static Version asVersion(@Nullable String str) throws InvalidVersionSpecificationException {
    return ourVersioning.parseVersion(str == null? "" : str);
  }

  public static VersionConstraint asVersionConstraint(@Nullable String str) throws InvalidVersionSpecificationException {
    return ourVersioning.parseVersionConstraint(str == null? "" : str);
  }

  private static List<Artifact> toArtifacts(String groupId, String artifactId, Collection<VersionConstraint> constraints, Set<ArtifactKind> kinds) {
    if (constraints.isEmpty() || kinds.isEmpty()) {
      return Collections.emptyList();
    }
    final List<Artifact> result = new ArrayList<>(kinds.size() * constraints.size());
    for (ArtifactKind kind : kinds) {
      for (VersionConstraint constraint : constraints) {
        result.add(new DefaultArtifact(groupId, artifactId, kind.getClassifier(), kind.getExtension(), constraint.toString()));
      }
    }
    return result;
  }

  private static class ArtifactWithChangedClassifier extends DelegatingArtifact {
    private final String myClassifier;

    ArtifactWithChangedClassifier(Artifact artifact, String classifier) {
      super(artifact);
      myClassifier = classifier;
    }

    @Override
    protected DelegatingArtifact newInstance(Artifact artifact) {
      return new ArtifactWithChangedClassifier(artifact, myClassifier);
    }

    @Override
    public String getClassifier() {
      return myClassifier;
    }
  }

  /**
   * Simplified copy of package-local org.eclipse.aether.internal.impl.ArtifactRequestBuilder
    */
  private static class ArtifactRequestBuilder implements DependencyVisitor {
    private final ArtifactKind myKind;
    private final List<ArtifactRequest> myRequests = new ArrayList<>();

    ArtifactRequestBuilder(ArtifactKind kind) {
      myKind = kind;
    }

    @Override
    public boolean visitEnter(DependencyNode node) {
      final Dependency dep = node.getDependency();
      if (dep != null) {
        Artifact artifact = dep.getArtifact();
        String classifier = myKind.getClassifier();
        if (classifier.isEmpty()) {
          myRequests.add(new ArtifactRequest(node));
        }
        else {
          myRequests.add(new ArtifactRequest(new ArtifactWithChangedClassifier(artifact, classifier),
            node.getRepositories(),
            node.getRequestContext()
          ));
        }
      }
      return true;
    }

    @Override
    public boolean visitLeave(DependencyNode node) {
      return true;
    }

    @NotNull
    public List<ArtifactRequest> getRequests() {
      return myRequests;
    }
  }

  private static class ExcludeDependenciesFilter implements DependencyFilter {
    private final HashSet<String> myExcludedDependencies;

    ExcludeDependenciesFilter(List<String> excludedDependencies) {
      myExcludedDependencies = new HashSet<>(excludedDependencies);
    }

    @Override
    public boolean accept(DependencyNode node, List<DependencyNode> parents) {
      Artifact artifact = node.getArtifact();
      if (artifact != null && myExcludedDependencies.contains(artifact.getGroupId() + ":" + artifact.getArtifactId())) {
        return false;
      }
      for (DependencyNode parent : parents) {
        Artifact parentArtifact = parent.getArtifact();
        if (parentArtifact != null && myExcludedDependencies.contains(parentArtifact.getGroupId() + ":" + parentArtifact.getArtifactId())) {
          return false;
        }
      }
      return true;
    }
  }

  private static class ArtifactDependencyTreeBuilder implements DependencyVisitor {
    private final List<List<ArtifactDependencyNode>> myCurrentChildren = new ArrayList<>();

    ArtifactDependencyTreeBuilder() {
      myCurrentChildren.add(new ArrayList<>());
    }

    @Override
    public boolean visitEnter(DependencyNode node) {
      Artifact artifact = node.getArtifact();
      if (artifact == null) return false;

      myCurrentChildren.add(new ArrayList<>());
      return true;
    }

    @Override
    public boolean visitLeave(DependencyNode node) {
      Artifact artifact = node.getArtifact();
      if (artifact != null) {
        List<ArtifactDependencyNode> last = myCurrentChildren.get(myCurrentChildren.size() - 1);
        myCurrentChildren.remove(myCurrentChildren.size() - 1);
        myCurrentChildren.get(myCurrentChildren.size() - 1).add(new ArtifactDependencyNode(artifact, last));
      }
      return true;
    }

    public ArtifactDependencyNode getRoot() {
      List<ArtifactDependencyNode> rootNodes = myCurrentChildren.get(0);
      return rootNodes.isEmpty() ? null : rootNodes.get(0);
    }
  }
}
