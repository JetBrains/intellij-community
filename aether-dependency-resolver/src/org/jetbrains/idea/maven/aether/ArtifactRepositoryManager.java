// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.aether;

import com.intellij.openapi.application.ClassPathUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ArrayUtil;
import org.apache.maven.model.Activation;
import org.apache.maven.model.Profile;
import org.apache.maven.model.building.DefaultModelBuilderFactory;
import org.apache.maven.model.building.ModelBuilder;
import org.apache.maven.model.building.ModelProblemCollector;
import org.apache.maven.model.profile.ProfileActivationContext;
import org.apache.maven.model.profile.activation.ProfileActivator;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.*;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.graph.*;
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
import org.eclipse.aether.util.graph.selector.AndDependencySelector;
import org.eclipse.aether.util.graph.selector.ExclusionDependencySelector;
import org.eclipse.aether.util.graph.transformer.ConflictResolver;
import org.eclipse.aether.util.graph.visitor.FilteringDependencyVisitor;
import org.eclipse.aether.util.graph.visitor.TreeDependencyVisitor;
import org.eclipse.aether.util.repository.AuthenticationBuilder;
import org.eclipse.aether.util.repository.SimpleResolutionErrorPolicy;
import org.eclipse.aether.util.version.GenericVersionScheme;
import org.eclipse.aether.version.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Aether-based repository manager and dependency resolver using maven implementation of this functionality.
 * <p>
 * instance of this component should be managed by the code which requires dependency resolution functionality
 * all necessary params like a path to local repo should be passed in constructor
 */
public final class ArtifactRepositoryManager {
  private static final Logger LOG = Logger.getInstance(ArtifactRepositoryManager.class);
  
  private static final VersionScheme ourVersioning = new GenericVersionScheme();
  private static final JreProxySelector ourProxySelector = new JreProxySelector();
  private final RepositorySystemSessionFactory mySessionFactory;

  private static final RepositorySystem ourSystem;
  static {
    DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
    locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
    locator.addService(TransporterFactory.class, FileTransporterFactory.class);
    locator.addService(TransporterFactory.class, HttpTransporterFactory.class);
    locator.setServices(ModelBuilder.class, new DefaultModelBuilderFactory() {
      @Override
      public ProfileActivator[] newProfileActivators() {
        // allow pom profiles to make dependency resolution deterministic and predictable:
        // consider all possible dependencies the artifact can potentially have.
        return new ProfileActivator[] {new ProfileActivatorProxy(super.newProfileActivators())};
      }
    }.newInstance());
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

  public ArtifactRepositoryManager(@NotNull File localRepositoryPath, @NotNull ProgressConsumer progressConsumer) {
    // recreate remote repository objects to ensure the latest proxy settings are used
    this(localRepositoryPath, createDefaultRemoteRepositories(), progressConsumer);
  }

  public ArtifactRepositoryManager(@NotNull File localRepositoryPath, List<RemoteRepository> remoteRepositories, @NotNull ProgressConsumer progressConsumer) {
    this(localRepositoryPath, remoteRepositories, progressConsumer, false);
  }

  public ArtifactRepositoryManager(@NotNull File localRepositoryPath, List<RemoteRepository> remoteRepositories, @NotNull ProgressConsumer progressConsumer, boolean offline) {
    this(localRepositoryPath, remoteRepositories, progressConsumer, offline, RetryProvider.disabled());
  }

  public ArtifactRepositoryManager(@NotNull File localRepositoryPath, List<RemoteRepository> remoteRepositories,
                                   @NotNull ProgressConsumer progressConsumer, @NotNull Retry retry) {
    this(localRepositoryPath, remoteRepositories, progressConsumer, false, retry);
  }

  public ArtifactRepositoryManager(@NotNull File localRepositoryPath, List<RemoteRepository> remoteRepositories,
                                   @NotNull ProgressConsumer progressConsumer, boolean offline, @NotNull Retry retry) {
    myRemoteRepositories.addAll(remoteRepositories);
    mySessionFactory = new RepositorySystemSessionFactory(localRepositoryPath, progressConsumer, offline, retry);
  }


  private static final class RepositorySystemSessionFactory {
    private final RepositorySystemSession sessionTemplate;
    private final Retry myRetry;

    private RepositorySystemSessionFactory(@NotNull File localRepositoryPath,
                                           @NotNull ProgressConsumer progressConsumer,
                                           boolean offline,
                                           Retry retry) {
      DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
      if (progressConsumer != ProgressConsumer.DEAF) {
        session.setTransferListener(new TransferListener() {
          @Override
          public void transferInitiated(TransferEvent event) throws TransferCancelledException {
            handle(event);
          }

          @Override
          public void transferStarted(TransferEvent event) throws TransferCancelledException {
            handle(event);
          }

          @Override
          public void transferProgressed(TransferEvent event) throws TransferCancelledException {
            handle(event);
          }

          @Override
          public void transferCorrupted(TransferEvent event) { }

          @Override
          public void transferSucceeded(TransferEvent event) { }

          @Override
          public void transferFailed(TransferEvent event) { }

          private void handle(TransferEvent event) throws TransferCancelledException {
            if (progressConsumer.isCanceled()) {
              throw new TransferCancelledException();
            }
            progressConsumer.consume(event.toString()); //NON-NLS
          }
        });
      }
      // setup session here
      session.setLocalRepositoryManager(ourSystem.newLocalRepositoryManager(session, new LocalRepository(localRepositoryPath)));
      session.setProxySelector(ourProxySelector);
      session.setOffline(offline);

      // Disable transfer errors caching to force re-request missing artifacts and metadata on network failures.
      // Despite this, some errors are still cached in session data, and for proper retries work we must reset this data after failure
      // what's performed by retryWithClearSessionData()
      var artifactCachePolicy = ResolutionErrorPolicy.CACHE_NOT_FOUND;
      var metadataCachePolicy = ResolutionErrorPolicy.CACHE_NOT_FOUND;
      session.setResolutionErrorPolicy(new SimpleResolutionErrorPolicy(artifactCachePolicy, metadataCachePolicy));

      session.setCache(new DefaultRepositoryCache());

      session.setReadOnly();
      sessionTemplate = session;
      myRetry = retry;
    }

    private RepositorySystemSession createDefaultSession() {
      DefaultRepositorySystemSession session = new DefaultRepositorySystemSession(sessionTemplate);
      session.setReadOnly();
      return session;
    }

    /**
     * Return session which will include dependencies rejected by conflict resolver to the results.
     * @see ArtifactDependencyNode#isRejected()
     */
    private RepositorySystemSession createVerboseSession() {
      DefaultRepositorySystemSession session = new DefaultRepositorySystemSession(sessionTemplate);
      session.setConfigProperty(ConflictResolver.CONFIG_PROP_VERBOSE, Boolean.TRUE);
      session.setReadOnly();
      return session;
    }

    private RepositorySystemSession createSession(@NotNull List<String> excludedDependencies) {
      DefaultRepositorySystemSession session = new DefaultRepositorySystemSession(sessionTemplate);
      if (!excludedDependencies.isEmpty()) {
        session.setDependencySelector(new AndDependencySelector(
          session.getDependencySelector(),
          new ExclusionDependencySelector(exclusions(excludedDependencies)))
        );
      }
      session.setReadOnly();
      return session;
    }

    @SuppressWarnings("SSBasedInspection")
    private static List<Exclusion> exclusions(List<String> excludedDependencies) {
      return excludedDependencies.stream().map(exclusion -> {
        String[] split = exclusion.split(":", 2);
        if (split.length != 2) {
          throw new RuntimeException("Malformed exclusion, 'groupId:artifactName' format is expected but got " + exclusion);
        }
        String groupId = split[0];
        String artifactName = split[1];
        return new Exclusion(groupId, artifactName, "*", "*");
      }).collect(Collectors.toList());
    }

    private <R> RetryWithClearSessionDataResult<R> retryWithClearSessionData(@NotNull RepositorySystemSession sessionTemplate,
                                                                             @NotNull ThrowingFunction<RepositorySystemSession, ? extends R> func)
      throws Exception {
      // Some errors are cached in session data, and for proper retries work, we must
      // reset this data with RepositorySystemSession#setData after failure.
      return myRetry.retry(() -> {
        RepositorySystemSession newSession = cloneSessionAndClearData(sessionTemplate);
        R result = func.get(newSession);
        return new RetryWithClearSessionDataResult<>(newSession, result);
      }, LOG);
    }

    private static RepositorySystemSession cloneSessionAndClearData(RepositorySystemSession session) {
      DefaultRepositorySystemSession newSession = new DefaultRepositorySystemSession(session);
      newSession.setData(new DefaultSessionData());
      newSession.setReadOnly();
      return newSession;
    }

    private static final class RetryWithClearSessionDataResult<R> {
      private final RepositorySystemSession session;
      private final R result;

      RetryWithClearSessionDataResult(RepositorySystemSession session, R result) {
        this.session = session;
        this.result = result;
      }

      private RepositorySystemSession getSession() {
        return session;
      }

      private R getResult() {
        return result;
      }
    }
  }

  /**
   * Returns list of classes corresponding to classpath entries for this module.
   */
  @SuppressWarnings("UnnecessaryFullyQualifiedName")
  public static Class<?>[] getClassesFromDependencies() {
    var result = new ArrayList<>(List.of(
      org.jetbrains.idea.maven.aether.ArtifactRepositoryManager.class, //this module
      org.apache.maven.repository.internal.VersionsMetadataGeneratorFactory.class, //maven-aether-provider
      org.apache.maven.artifact.Artifact.class, //maven-artifact
      org.apache.commons.lang3.StringUtils.class, //commons-lang3
      org.codehaus.plexus.util.Base64.class, //plexus-utils
      org.apache.maven.building.Problem.class, //maven-builder-support
      org.apache.maven.model.Model.class, //maven-model
      org.apache.maven.model.building.ModelBuilder.class, //maven-model-builder
      org.apache.maven.artifact.repository.metadata.Metadata.class, //maven-repository-metadata
      org.codehaus.plexus.interpolation.Interpolator.class, //plexus-interpolation
      org.eclipse.aether.RepositorySystem.class, //aether-api
      org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory.class, //aether-connector-basic
      org.eclipse.aether.spi.connector.RepositoryConnector.class, //aether-spi
      org.eclipse.aether.util.ConfigUtils.class, //aether-util
      org.eclipse.aether.impl.ArtifactResolver.class, //aether-impl
      org.eclipse.sisu.Nullable.class, // sisu.inject
      org.eclipse.aether.transport.file.FileTransporterFactory.class, //aether-transport-file
      org.eclipse.aether.transport.http.HttpTransporterFactory.class, //aether-transport-http
      org.apache.http.HttpConnection.class, //http-core
      org.apache.http.client.HttpClient.class, //http-client
      org.apache.http.entity.mime.MIME.class, //http-mime
      org.apache.commons.logging.LogFactory.class, // commons-logging
      org.slf4j.Marker.class, // slf4j, - required for aether resolver at runtime
      org.slf4j.jul.JDK14LoggerFactory.class, // slf4j-jdk14 - required for aether resolver at runtime
      org.eclipse.aether.named.providers.NoopNamedLockFactory.class, // resolver-named-locks
      org.apache.commons.codec.binary.Base64.class // commons-codec
    ));
    result.addAll(List.of(ClassPathUtil.getUtilClasses())); // intellij.platform.util module

    return result.toArray(ArrayUtil.EMPTY_CLASS_ARRAY);
  }

  public @NotNull Collection<File> resolveDependency(String groupId,
                                                     String artifactId,
                                                     String version,
                                                     boolean includeTransitiveDependencies,
                                                     List<String> excludedDependencies) throws Exception {
    Collection<Artifact> artifacts = resolveDependencyAsArtifact(groupId, artifactId, version, EnumSet.of(ArtifactKind.ARTIFACT),
                                                                 includeTransitiveDependencies, excludedDependencies);
    if (artifacts.isEmpty()) {
      return Collections.emptyList();
    }

    List<File> files = new ArrayList<>(artifacts.size());
    for (Artifact artifact : artifacts) {
      files.add(artifact.getFile());
    }
    return files;
  }

  @Nullable
  public ArtifactDependencyNode collectDependencies(String groupId, String artifactId, String versionConstraint) throws Exception {
    Set<VersionConstraint> constraints = Collections.singleton(asVersionConstraint(versionConstraint));
    CollectRequest collectRequest = createCollectRequest(groupId, artifactId, constraints, EnumSet.of(ArtifactKind.ARTIFACT));
    ArtifactDependencyTreeBuilder builder = new ArtifactDependencyTreeBuilder();

    DependencyNode root = mySessionFactory.retryWithClearSessionData(
      mySessionFactory.createVerboseSession(),
      s -> ourSystem.collectDependencies(s, collectRequest)
    ).getResult().getRoot();

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
    List<Artifact> artifacts = new ArrayList<>();
    VersionConstraint originalConstraints = asVersionConstraint(versionConstraint);
    for (ArtifactKind kind : artifactKinds) {
      // RepositorySystem.resolveDependencies() ignores classifiers, so we need to set classifiers explicitly for discovered dependencies.
      // Because of that, we have to first discover deps and then resolve corresponding artifacts
      try {
        List<ArtifactRequest> requests = new ArrayList<>();
        Set<VersionConstraint> constraints;
        if (kind == ArtifactKind.ANNOTATIONS) {
          constraints = relaxForAnnotations(originalConstraints);
        } else {
          constraints = Collections.singleton(originalConstraints);
        }
        RepositorySystemSession session = prepareRequests(groupId, artifactId, constraints, kind, includeTransitiveDependencies, excludedDependencies, requests);

        if (LOG.isDebugEnabled()) {
          LOG.debug("Resolving " + groupId + ":" + artifactId + ":" + versionConstraint +
                    " transitiveDependencies=" + includeTransitiveDependencies +
                    " excludedDependencies=" + excludedDependencies +
                    " kind=" + kind +
                    " requests=" + requests);
        }

        if (!requests.isEmpty()) {
          try {
            List<ArtifactResult> resultList = mySessionFactory.retryWithClearSessionData(
              session,
              s -> ourSystem.resolveArtifacts(s, requests)
            ).getResult();

            for (ArtifactResult result : resultList) {
              artifacts.add(result.getArtifact());
            }
          }
          catch (ArtifactResolutionException e) {
            if (kind != ArtifactKind.ARTIFACT) {
              // for sources and javadocs, try to process requests one-by-one and fetch at least something
              if (requests.size() > 1) {
                for (ArtifactRequest request : requests) {
                  try {
                    // Don't retry on sources or javadocs resolution: used only in IDE, will only waste user's time if the artifact does not
                    // exist.
                    ArtifactResult result = ourSystem.resolveArtifact(session, request);
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

  @NotNull
  private RepositorySystemSession prepareRequests(String groupId,
                                                  String artifactId,
                                                  Set<VersionConstraint> constraints,
                                                  ArtifactKind kind,
                                                  boolean includeTransitiveDependencies,
                                                  List<String> excludedDependencies,
                                                  List<ArtifactRequest> requests) throws Exception {
    RepositorySystemSession session;
    if (includeTransitiveDependencies) {
      CollectRequest collectRequest = createCollectRequest(groupId, artifactId, constraints, EnumSet.of(kind));
      var resultAndSession = mySessionFactory.retryWithClearSessionData(
        mySessionFactory.createSession(excludedDependencies),
        (s) -> ourSystem.collectDependencies(s, collectRequest)
      );
      session = resultAndSession.getSession();
      CollectResult collectResult = resultAndSession.getResult();

      ArtifactRequestBuilder builder = new ArtifactRequestBuilder(kind);
      DependencyFilter filter = createScopeFilter();
      if (!excludedDependencies.isEmpty()) {
        filter = DependencyFilterUtils.andFilter(filter, new ExcludeDependenciesFilter(excludedDependencies));
      }
      collectResult.getRoot().accept(new TreeDependencyVisitor(new FilteringDependencyVisitor(builder, filter)));
      requests.addAll(builder.getRequests());
    }
    else {
      session = mySessionFactory.createDefaultSession();
      for (Artifact artifact : toArtifacts(groupId, artifactId, constraints, Collections.singleton(kind))) {
        if (ourVersioning.parseVersionConstraint(artifact.getVersion()).getRange() != null) {
          VersionRangeRequest versionRangeRequest = new VersionRangeRequest(artifact, Collections.unmodifiableList(myRemoteRepositories), null);
          VersionRangeResult result = ourSystem.resolveVersionRange(session, versionRangeRequest);
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
    return session;
  }

  @NotNull
  public Collection<Artifact> resolveDependencyAsArtifactStrict(String groupId, String artifactId, String versionConstraint,
                                                                ArtifactKind kind, boolean includeTransitiveDependencies,
                                                                List<String> excludedDependencies) throws Exception {
    List<Artifact> artifacts = new ArrayList<>();
    List<ArtifactRequest> requests = new ArrayList<>();
    Set<VersionConstraint> constraints = Collections.singleton(asVersionConstraint(versionConstraint));
    RepositorySystemSession session = prepareRequests(groupId, artifactId, constraints, kind, includeTransitiveDependencies, excludedDependencies, requests);

    if (!requests.isEmpty()) {
      List<ArtifactResult> resultList = mySessionFactory.retryWithClearSessionData(
        session,
        (s) -> ourSystem.resolveArtifacts(s, requests)
      ).getResult();

      for (ArtifactResult result : resultList) {
        artifacts.add(result.getArtifact());
      }
    }
    return artifacts;
  }

  /**
   * Modify version constraint to look for applicable "annotations" artifact.
   * <p>
   * "Annotations" artifact for a given library is matched by Group ID, Artifact ID, and classifier "annotations".
   * "Annotations" version is selected using the following rules:
   * <ul>
   *   <li>it is larger or equal to major component of library version (or lower constraint bound).
   *   E.g., annotations artifact ver 3.1 is applicable to library ver 3.6.5 (3.1 > 3.0)</li>
   *   <li>it is smaller or equal to library version with suffix (-an10000).
   *   E.g., annotations artifact ver 3.2-an3 is applicable to library ver 3.2</li>
   * </ul>
   * This allows to re-use existing annotations artifacts across different library versions
   * @param constraint - version or range constraint of an original library
   * @return resulting relaxed constraint to select "annotations" artifact.
   */
  private static Set<VersionConstraint> relaxForAnnotations(VersionConstraint constraint) {
    String annotationsConstraint = constraint.toString();

    Version version = constraint.getVersion();
    if (version != null) {
      String lower = chooseLowerBoundString(version);
      annotationsConstraint = "[" + lower + ", " + version + "-an10000]";
    }

    VersionRange range = constraint.getRange();
    if (range != null) {
      Version lowerBoundVersion = range.getLowerBound().getVersion();

      String lower = chooseLowerBoundString(lowerBoundVersion);
      String upper = range.getUpperBound().isInclusive()
                     ? range.getUpperBound().toString() + "-an10000]"
                     : range.getUpperBound().toString() + ")";
      annotationsConstraint = "[" + lower + ", " + upper;
    }

    try {
      return Collections.singleton(ourVersioning.parseVersionConstraint(annotationsConstraint));
    } catch (InvalidVersionSpecificationException e) {
      LOG.info("Failed to parse version constraint " + annotationsConstraint, e);
    }

    return Collections.singleton(constraint);
  }

  private static String chooseLowerBoundString(Version lowerBoundVersion) {
    String lowerBoundString = lowerBoundVersion.toString();
    String candidate = lowerBoundString.split("[.\\-_]")[0];
    try {
      Version candidateVersion = ourVersioning.parseVersion(candidate);
      if (lowerBoundVersion.compareTo(candidateVersion) < 0) {
       return lowerBoundString;
      }
    } catch (InvalidVersionSpecificationException e) {
      LOG.info("Failed to parse major part of lower bound of version " + lowerBoundVersion, e);
    }
    return candidate;
  }

  @NotNull
  private static DependencyFilter createScopeFilter() {
    return DependencyFilterUtils.classpathFilter(JavaScopes.COMPILE, JavaScopes.RUNTIME);
  }

  /**
   * Gets the versions (in ascending order) that matched the requested range.
   */
  @NotNull
  public List<Version> getAvailableVersions(String groupId, String artifactId, String versionConstraint, ArtifactKind artifactKind) throws Exception {
    VersionRangeResult result = ourSystem.resolveVersionRange(
      mySessionFactory.createDefaultSession(), createVersionRangeRequest(groupId, artifactId, asVersionConstraint(versionConstraint), artifactKind)
    );
    return result.getVersions();
  }

  public static RemoteRepository createRemoteRepository(String id, String url) {
    return createRemoteRepository(id, url, null, true);
  }

  public static RemoteRepository createRemoteRepository(String id, String url,
                                                        boolean allowSnapshots) {
    return createRemoteRepository(id, url, null, allowSnapshots);
  }

  public static RemoteRepository createRemoteRepository(String id, String url, ArtifactAuthenticationData authenticationData) {
    return createRemoteRepository(id, url, authenticationData, true);
  }

  public static RemoteRepository createRemoteRepository(String id, String url, ArtifactAuthenticationData authenticationData, boolean allowSnapshots) {
    // for maven repos repository type should be 'default'
    RemoteRepository.Builder builder = new RemoteRepository.Builder(id, "default", url);

    // explicitly set UPDATE_POLICY_ALWAYS, because default setting is UPDATE_POLICY_DAILY, and 5xx resolution errors are cached
    // in local repository for one day and retry does not work
    RepositoryPolicy enabledRepositoryPolicy = new RepositoryPolicy(true, RepositoryPolicy.UPDATE_POLICY_ALWAYS, RepositoryPolicy.CHECKSUM_POLICY_WARN);
    RepositoryPolicy disabledRepositoryPolicy = new RepositoryPolicy(false, null, RepositoryPolicy.CHECKSUM_POLICY_WARN);
    builder.setReleasePolicy(enabledRepositoryPolicy);
    builder.setSnapshotPolicy(allowSnapshots ? enabledRepositoryPolicy : disabledRepositoryPolicy);

    if (authenticationData != null) {
      AuthenticationBuilder authenticationBuilder = new AuthenticationBuilder();
      authenticationBuilder.addUsername(authenticationData.getUsername());
      authenticationBuilder.addPassword(authenticationData.getPassword());
      builder.setAuthentication(authenticationBuilder.build());
    }
    return builder.setProxy(ourProxySelector.getProxy(url)).build();
  }

  private static RemoteRepository createRemoteRepository(RemoteRepository prototype) {
    String url = prototype.getUrl();
    return new RemoteRepository.Builder(prototype.getId(), prototype.getContentType(), url).setProxy(ourProxySelector.getProxy(url)).build();
  }

  @NotNull
  public static List<RemoteRepository> createDefaultRemoteRepositories() {
    return List.of(
      // Maven Central Repository
      createRemoteRepository(
        "central", "https://repo1.maven.org/maven2/"
      ),

      // JBoss Community Repository
      createRemoteRepository(
        "jboss.community", "https://repository.jboss.org/nexus/content/repositories/public/"
      )
    );
  }

  private CollectRequest createCollectRequest(String groupId, String artifactId, Collection<VersionConstraint> versions, Set<ArtifactKind> kinds) {
    CollectRequest request = new CollectRequest();
    for (Artifact artifact : toArtifacts(groupId, artifactId, versions, kinds)) {
      request.addDependency(new Dependency(artifact, JavaScopes.COMPILE));
    }
    return request.setRepositories(Collections.unmodifiableList(myRemoteRepositories));
  }

  private VersionRangeRequest createVersionRangeRequest(String groupId, String artifactId, VersionConstraint versioning, ArtifactKind artifactKind) {
    VersionRangeRequest request = new VersionRangeRequest();
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

  private static VersionConstraint asVersionConstraint(@Nullable String str) throws InvalidVersionSpecificationException {
    return ourVersioning.parseVersionConstraint(str == null? "" : str);
  }

  private static List<Artifact> toArtifacts(String groupId, String artifactId, Collection<VersionConstraint> constraints, Set<ArtifactKind> kinds) {
    if (constraints.isEmpty() || kinds.isEmpty()) {
      return Collections.emptyList();
    }
    List<Artifact> result = new ArrayList<>(kinds.size() * constraints.size());
    for (ArtifactKind kind : kinds) {
      for (VersionConstraint constraint : constraints) {
        result.add(new DefaultArtifact(groupId, artifactId, kind.getClassifier(), kind.getExtension(), constraint.toString()));
      }
    }
    return result;
  }

  public static class ArtifactAuthenticationData {
    private final String username;
    private final String password;

    public ArtifactAuthenticationData(String username, String password) {
      this.username = username;
      this.password = password;
    }

    private String getUsername() {
      return username;
    }

    private String getPassword() {
      return password;
    }
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
  private static final class ArtifactRequestBuilder implements DependencyVisitor {
    private final ArtifactKind myKind;
    private final List<ArtifactRequest> myRequests = new ArrayList<>();

    private ArtifactRequestBuilder(ArtifactKind kind) {
      myKind = kind;
    }

    @Override
    public boolean visitEnter(DependencyNode node) {
      Dependency dep = node.getDependency();
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

  private static final class ExcludeDependenciesFilter implements DependencyFilter {
    private final HashSet<String> myExcludedDependencies;

    private ExcludeDependenciesFilter(List<String> excludedDependencies) {
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

  private static final class ArtifactDependencyTreeBuilder implements DependencyVisitor {
    private final List<List<ArtifactDependencyNode>> myCurrentChildren = new ArrayList<>();

    private ArtifactDependencyTreeBuilder() {
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
        boolean rejected = node.getData().get(ConflictResolver.NODE_DATA_WINNER) != null;
        myCurrentChildren.get(myCurrentChildren.size() - 1).add(new ArtifactDependencyNode(artifact, last, rejected));
      }
      return true;
    }

    public ArtifactDependencyNode getRoot() {
      List<ArtifactDependencyNode> rootNodes = myCurrentChildren.get(0);
      return rootNodes.isEmpty() ? null : rootNodes.get(0);
    }
  }

  // Force certain activation kinds to be always active in order to include such dependencies in dependency resolution process
  // Currently JDK activations are always enabled for the purpose of transitive artifact discovery
  private static class ProfileActivatorProxy implements ProfileActivator {

    private final ProfileActivator[] myDelegates;

    ProfileActivatorProxy(ProfileActivator[] delegates) {
      myDelegates = delegates;
    }

    private static boolean isForceActivation(Profile profile) {
      Activation activation = profile.getActivation();
      return activation != null && activation.getJdk() != null;
    }

    @Override
    public boolean isActive(Profile profile, ProfileActivationContext context, ModelProblemCollector problems) {
      if (isForceActivation(profile)) {
        return true;
      }
      Boolean active = null;
      for (ProfileActivator delegate : myDelegates) {
        if (delegate.presentInConfig(profile, context, problems)) {
          boolean activeValue = delegate.isActive(profile, context, problems);
          active = active == null? activeValue : active && activeValue;
        }
      }
      return Boolean.TRUE.equals(active);
    }

    @Override
    public boolean presentInConfig(Profile profile, ProfileActivationContext context, ModelProblemCollector problems) {
      if (isForceActivation(profile)) {
        return true;
      }
      for (ProfileActivator delegate : myDelegates) {
        if (delegate.presentInConfig(profile, context, problems)) {
          return true;
        }
      }
      return false;
    }
  }
}
