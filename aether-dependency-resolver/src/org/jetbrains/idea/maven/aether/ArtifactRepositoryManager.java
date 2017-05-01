package org.jetbrains.idea.maven.aether;

import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.graph.Dependency;
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
import org.eclipse.aether.util.artifact.JavaScopes;
import org.eclipse.aether.util.filter.DependencyFilterUtils;
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
  public Collection<Artifact> resolveDependencyAsArtifact(String groupId, String artifactId, String versionConstraint, final Set<ArtifactKind> artifactKinds) throws Exception {
    final List<Artifact> artifacts = new ArrayList<>();
    final Set<VersionConstraint> constraints = Collections.singleton(asVersionConstraint(versionConstraint));

    for (ArtifactKind kind : artifactKinds) {
      try {
        final DependencyRequest dependencyRequest = new DependencyRequest(
          createCollectRequest(groupId, artifactId, constraints, EnumSet.of(kind)),
          DependencyFilterUtils.classpathFilter(JavaScopes.COMPILE)
        );
        final DependencyResult result = ourSystem.resolveDependencies(mySession, dependencyRequest);
        for (ArtifactResult artifactResult : result.getArtifactResults()) {
          artifacts.add(artifactResult.getArtifact());
        }
      }
      catch (DependencyResolutionException e) {
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

}
