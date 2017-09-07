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
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.DependencyVisitor;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
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

import java.io.File;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: 20-Jun-16
 *
 * Aether-based repository manager and dependency resolver using maven implementation of this functionality.
 *
 * instance of this component should be managed by the code which requires dependency resolution functionality
 * all necessary params like path to local repo should be passed in constructor
 *
 */
public class ArtifactRepositoryManager {
  private static final VersionScheme ourVersioning = new GenericVersionScheme();
  private final DefaultRepositorySystemSession mySession;

  public static final RemoteRepository MAVEN_CENTRAL_REPOSITORY = createRemoteRepository(
    "central", "http://repo1.maven.org/maven2/"
  );
  public static final RemoteRepository JBOSS_COMMUNITY_REPOSITORY = createRemoteRepository(
    "jboss.community", "https://repository.jboss.org/nexus/content/repositories/public/"
  );
  public static final List<RemoteRepository> PREDEFINED_REMOTE_REPOSITORIES = Collections.unmodifiableList(Arrays.asList(
    MAVEN_CENTRAL_REPOSITORY, JBOSS_COMMUNITY_REPOSITORY
  ));

  private static final RepositorySystem ourSystem;
  static {
    final DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
    locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
    locator.addService(TransporterFactory.class, FileTransporterFactory.class);
    locator.addService(TransporterFactory.class, HttpTransporterFactory.class);
    locator.setErrorHandler(new DefaultServiceLocator.ErrorHandler() {
      public void serviceCreationFailed(Class<?> type, Class<?> impl, Throwable exception) {
        if (exception != null) {
          throw new RuntimeException(exception);
        }
      }
    });
    ourSystem = locator.getService(RepositorySystem.class);
  }

  private List<RemoteRepository> myRemoteRepositories = new ArrayList<>();

  public ArtifactRepositoryManager(@NotNull File localRepositoryPath) {
    this(localRepositoryPath, ProgressConsumer.DEAF);
  }

  public ArtifactRepositoryManager(@NotNull File localRepositoryPath, @NotNull final ProgressConsumer progressConsumer) {
    this(localRepositoryPath, PREDEFINED_REMOTE_REPOSITORIES, progressConsumer);
  }

  public ArtifactRepositoryManager(@NotNull File localRepositoryPath, List<RemoteRepository> remoteRepositories, @NotNull final ProgressConsumer progressConsumer) {
    myRemoteRepositories.addAll(remoteRepositories);
    final DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
    if (progressConsumer != ProgressConsumer.DEAF) {
      session.setTransferListener((TransferListener)Proxy
        .newProxyInstance(session.getClass().getClassLoader(), new Class[]{TransferListener.class}, new InvocationHandler() {
          private final EnumSet<TransferEvent.EventType> checkCancelEvents = EnumSet.of(TransferEvent.EventType.INITIATED, TransferEvent.EventType.STARTED, TransferEvent.EventType.PROGRESSED);
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
    session.setReadOnly();
    mySession = session;
  }


  public void addRemoteRepository(final String id, final String url) {
    myRemoteRepositories.add(createRemoteRepository(id, url));
  }

  public Collection<File> resolveDependency(String groupId, String artifactId, String version) throws Exception {
    final List<File> files = new ArrayList<>();
    for (Artifact artifact : resolveDependencyAsArtifact(groupId, artifactId, version, EnumSet.of(ArtifactKind.ARTIFACT))) {
      files.add(artifact.getFile());
    }
    return files;
  }

  @NotNull
  public Collection<Artifact> resolveDependencyAsArtifact(String groupId,
                                                          String artifactId,
                                                          String versionConstraint,
                                                          final Set<ArtifactKind> artifactKinds) throws Exception {
    
    final List<Artifact> artifacts = new ArrayList<>();
    final Set<VersionConstraint> constraints = Collections.singleton(asVersionConstraint(versionConstraint));
    for (ArtifactKind kind : artifactKinds) {
      //RepositorySystem.resolveDependencies() ignores classifiers, so we need to collect dependencies for the default classifier, and then
      // resolve artifacts with specified classifiers for each found dependency
      try {
        final CollectResult collectResult = ourSystem.collectDependencies(
          mySession, createCollectRequest(groupId, artifactId, constraints, EnumSet.of(kind))
        );
        final ArtifactRequestBuilder builder = new ArtifactRequestBuilder(kind);
        collectResult.getRoot().accept(new TreeDependencyVisitor(
          new FilteringDependencyVisitor(builder, DependencyFilterUtils.classpathFilter(JavaScopes.COMPILE))
        ));
    
        final List<ArtifactRequest> requests = builder.getRequests();
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

  public List<Version> getAvailableVersions(String groupId, String artifactId, String versionConstraint, final ArtifactKind artifactKind) throws Exception {
    final VersionRangeResult result = ourSystem.resolveVersionRange(
      mySession, createVersionRangeRequest(groupId, artifactId, asVersionConstraint(versionConstraint), artifactKind)
    );
    return result.getVersions();
  }

  public static RemoteRepository createRemoteRepository(final String id, final String url) {
    // for maven repos repository type should be 'default'
    return new RemoteRepository.Builder(id, "default", url).build();
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
    return request.setRepositories(Collections.unmodifiableList(myRemoteRepositories));
  }

  public static Version asVersion(@Nullable String str) throws InvalidVersionSpecificationException {
    return ourVersioning.parseVersion(str == null? "" : str);
  }
  
  public static VersionRange asVersionRange(@Nullable String str) throws InvalidVersionSpecificationException {
    return ourVersioning.parseVersionRange(str == null? "" : str);
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
      for (VersionConstraint constr : constraints) {
        result.add(new DefaultArtifact(groupId, artifactId, kind.getClassifier(), kind.getExtension(), constr.toString()));
      }
    }
    return result;
  }

  private static class ArtifactWithChangedClassifier extends DelegatingArtifact {
    private final String myClassifier;

    public ArtifactWithChangedClassifier(Artifact artifact, String classifier) {
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
    private List<ArtifactRequest> myRequests = new ArrayList<>();

    public ArtifactRequestBuilder(ArtifactKind kind) {
      myKind = kind;
    }

    public boolean visitEnter(DependencyNode node) {
      final Dependency dep = node.getDependency();
      if (dep != null) {
        myRequests.add(new ArtifactRequest(
          new ArtifactWithChangedClassifier(node.getDependency().getArtifact(), myKind.getClassifier()), 
          node.getRepositories(), 
          node.getRequestContext()
        ));
      }
      return true;
    }

    public boolean visitLeave(DependencyNode node) {
      return true;
    }

    @NotNull
    public List<ArtifactRequest> getRequests() {
      return myRequests;
    }
  }
}
