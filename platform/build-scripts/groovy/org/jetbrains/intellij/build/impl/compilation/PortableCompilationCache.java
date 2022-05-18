// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.compilation;

import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.io.NioFiles;
import com.intellij.openapi.util.text.Strings;
import groovy.lang.Closure;
import groovy.lang.Lazy;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.codehaus.groovy.runtime.StringGroovyMethods;
import org.jetbrains.intellij.build.BuildOptions;
import org.jetbrains.intellij.build.CompilationContext;
import org.jetbrains.intellij.build.CompilationTasks;
import org.jetbrains.intellij.build.impl.JpsCompilationRunner;
import org.jetbrains.intellij.build.impl.compilation.cache.CommitsHistory;
import org.jetbrains.intellij.build.impl.compilation.cache.CompilationOutput;
import org.jetbrains.jps.incremental.storage.ProjectStamps;

import java.io.File;
import java.util.*;

/**
 * Combination of {@link JpsCaches} and {@link CompilationOutput}s
 */
public final class PortableCompilationCache {
  public PortableCompilationCache(CompilationContext context) {
    this.context = context;
    this.git = new Git(context.getPaths().getProjectHome().trim());
  }

  /**
   * Download latest available {@link PortableCompilationCache} and perform incremental compilation if necessary
   * <p>
   * When force rebuilding incremental compilation flag has to be set to false otherwise backward-refs won't be created.
   * During rebuild JPS checks {@code CompilerReferenceIndex.exists(buildDir) || isRebuild} and if
   * incremental compilation is enabled JPS won't create {@link JavaBackwardReferenceIndexWriter}.
   * For more details see {@link JavaBackwardReferenceIndexWriter#initialize}
   */
  public Boolean downloadCacheAndCompileProject() {
    //noinspection GroovySynchronizationOnNonFinalField
    synchronized (org.jetbrains.intellij.build.impl.compilation.PortableCompilationCache) {
      if (isAlreadyUpdated) {
        context.getMessages().info(getClass().getSimpleName() + " is already updated");
        return;
      }

      if (forceRebuild) {
        clean();
      }
      else if (!isLocalCacheUsed()) {
        downloadCache();
      }

      CompilationTasks.create(context).resolveProjectDependencies();
      if (isCompilationRequired()) {
        context.getOptions().setIncrementalCompilation(!forceRebuild);
        compileProject();
      }

      isAlreadyUpdated = true;
      context.getOptions().setIncrementalCompilation(true);
      return setUseCompiledClassesFromProjectOutput(context.getOptions(), false);
    }
  }

  public boolean isCompilationRequired() {
    return forceRebuild || isLocalCacheUsed() || isRemoteCacheStale();
  }

  private boolean isLocalCacheUsed() {
    return !forceRebuild && !forceDownload && jpsCaches.getMaybeAvailableLocally();
  }

  private boolean isRemoteCacheStale() {
    return !downloader.getAvailableForHeadCommit() || downloader.getAnyLocalChanges();
  }

  /**
   * Upload local {@link PortableCompilationCache} to {@link RemoteCache}
   */
  public Object upload() {
    if (!forceRebuild && downloader.getAvailableForHeadCommit()) {
      context.getMessages().info("Nothing new to upload");
    }
    else {
      uploader.upload();
    }
  }

  /**
   * Publish already uploaded {@link PortableCompilationCache} to {@link RemoteCache}
   */
  public void publish() {
    uploader.updateCommitHistory();
  }

  public File buildJpsCacheZip() {
    return uploader.buildJpsCacheZip();
  }

  /**
   * Publish already uploaded {@link PortableCompilationCache} to {@link RemoteCache} overriding existing {@link CommitsHistory}.
   * Used in force rebuild and cleanup.
   */
  public void overrideCommitHistory(Set<String> forceRebuiltCommits) {
    LinkedHashMap<String, Set<String>> map = new LinkedHashMap<String, Set<String>>(1);
    map.put(remoteGitUrl, forceRebuiltCommits);
    CommitsHistory newCommitHistory = new CommitsHistory(map);
    uploader.updateCommitHistory(newCommitHistory, true);
  }

  private List<File> clean() {
    return DefaultGroovyMethods.each(new ArrayList<File>(Arrays.asList(jpsCaches.getDir(), context.getProjectOutputDirectory())),
                                     new Closure<Void>(this, this) {
                                       public void doCall(File it) {
                                         context.getMessages().info("Cleaning " + String.valueOf(it));
                                         NioFiles.deleteRecursively(it.toPath());
                                       }

                                       public void doCall() {
                                         doCall(null);
                                       }
                                     });
  }

  private Object compileProject() {
    // fail-fast in case of KTIJ-17296
    if (SystemInfoRt.isWindows && !git.lineBreaksConfig().equals("input")) {
      context.getMessages().error(getClass().getSimpleName() +
                                  " cannot be used with CRLF line breaks, ".plus(
                                      "please execute `git config --global core.autocrlf input` before checkout ")
                                    .plus("and upvote https://youtrack.jetbrains.com/issue/KTIJ-17296"));
    }

    JpsCompilationRunner jps = new JpsCompilationRunner(context);
    try {
      jps.buildAll();
    }
    catch (Exception e) {
      if (context.getOptions().getIncrementalCompilation() && !forceDownload) {
        // Portable Compilation Cache is rebuilt from scratch on CI and re-published every night to avoid possible incremental compilation issues.
        // If download isn't forced then locally available cache will be used which may suffer from those issues.
        // Hence compilation failure. Replacing local cache with remote one may help.
        context.getMessages().warning("Incremental compilation using locally available caches failed. " + "Re-trying using Remote Cache.");
        downloadCache();
        jps.buildAll();
      }
      else {
        throw e;
      }
    }

    return null;
  }

  private void downloadCache() {
    try {
      downloader.download();
    }
    finally {
      downloader.close();
    }
  }

  private String require(String systemProperty, String description) {
    String value = System.getProperty(systemProperty);
    if (Strings.isEmptyOrSpaces(value)) {
      context.getMessages().error(description + " is not defined. Please set \'" + systemProperty + "\' system property.");
    }

    return ((String)(value));
  }

  private static boolean bool(String systemProperty, boolean defaultValue) {
    return StringGroovyMethods.toBoolean(System.getProperty(systemProperty, String.valueOf(defaultValue)));
  }

  public static boolean getCAN_BE_USED() {
    return CAN_BE_USED;
  }

  public static boolean isCAN_BE_USED() {
    return CAN_BE_USED;
  }

  public final boolean getCanBeUsed() {
    return canBeUsed;
  }

  public final boolean isCanBeUsed() {
    return canBeUsed;
  }

  private final CompilationContext context;
  private final Git git;
  /**
   * IntelliJ repository git remote url
   */
  private static final String GIT_REPOSITORY_URL_PROPERTY = "intellij.remote.url";
  /**
   * If true then {@link PortableCompilationCache} for head commit is expected to exist and search in
   * {@link CommitsHistory#JSON_FILE} is skipped.
   * Required for temporary branch caches which are uploaded but not published in
   * {@link CommitsHistory#JSON_FILE}.
   */
  private static final String AVAILABLE_FOR_HEAD_PROPERTY = "intellij.jps.cache.availableForHeadCommit";
  /**
   * Download {@link PortableCompilationCache} even if there are caches available locally
   */
  private static final String FORCE_DOWNLOAD_PROPERTY = "intellij.jps.cache.download.force";
  /**
   * If true then {@link PortableCompilationCache} will be rebuilt from scratch
   */
  private static final String FORCE_REBUILD_PROPERTY = "intellij.jps.cache.rebuild.force";
  /**
   * Folder to store {@link PortableCompilationCache} for later upload to AWS S3 bucket.
   * Upload performed in a separate process on CI.
   */
  private static final String AWS_SYNC_FOLDER_PROPERTY = "jps.caches.aws.sync.folder";
  /**
   * Commit hash for which {@link PortableCompilationCache} is to be built/downloaded
   */
  private static final String COMMIT_HASH_PROPERTY = "build.vcs.number";
  private static final boolean CAN_BE_USED = ProjectStamps.PORTABLE_CACHES && RemoteCache.IS_CONFIGURED;
  private final boolean forceDownload = bool(FORCE_DOWNLOAD_PROPERTY, false);
  private final boolean forceRebuild = bool(FORCE_REBUILD_PROPERTY, false);
  private final RemoteCache remoteCache = new RemoteCache();
  @Lazy private JpsCaches jpsCaches = new Closure<JpsCaches>(this, this) {
    public JpsCaches doCall(Object it) { return new JpsCaches(context); }

    public JpsCaches doCall() {
      return doCall(null);
    }
  }.call(new Closure<JpsCaches>(this, this) {
    public JpsCaches doCall(Object it) { return new JpsCaches(context); }

    public JpsCaches doCall() {
      return doCall(null);
    }
  });
  private final boolean canBeUsed = CAN_BE_USED;
  @Lazy private String remoteGitUrl = new Closure<String>(this, this) {
    public String doCall(Object it) {
      return DefaultGroovyMethods.tap(require(GIT_REPOSITORY_URL_PROPERTY, "Repository url"),
                                      new Closure<Void>(PortableCompilationCache.this, PortableCompilationCache.this) {
                                        public void doCall(String it) {
                                          context.getMessages().info("Git remote url " + it);
                                        }

                                        public void doCall() {
                                          doCall(null);
                                        }
                                      });
    }

    public String doCall() {
      return doCall(null);
    }
  }.call(new Closure<String>(this, this) {
    public String doCall(Object it) {
      return DefaultGroovyMethods.tap(require(GIT_REPOSITORY_URL_PROPERTY, "Repository url"),
                                      new Closure<Void>(PortableCompilationCache.this, PortableCompilationCache.this) {
                                        public void doCall(String it) {
                                          context.getMessages().info("Git remote url " + it);
                                        }

                                        public void doCall() {
                                          doCall(null);
                                        }
                                      });
    }

    public String doCall() {
      return doCall(null);
    }
  });
  @Lazy private PortableCompilationCacheDownloader downloader = new Closure<PortableCompilationCacheDownloader>(this, this) {
    public PortableCompilationCacheDownloader doCall(Object it) {
      boolean availableForHeadCommit = bool(AVAILABLE_FOR_HEAD_PROPERTY, false);
      return new PortableCompilationCacheDownloader(context, git, remoteCache.getUrl(), remoteGitUrl, availableForHeadCommit,
                                                    jpsCaches.getSkipDownload());
    }

    public PortableCompilationCacheDownloader doCall() {
      return doCall(null);
    }
  }.call(new Closure<PortableCompilationCacheDownloader>(this, this) {
    public PortableCompilationCacheDownloader doCall(Object it) {
      boolean availableForHeadCommit = bool(AVAILABLE_FOR_HEAD_PROPERTY, false);
      return new PortableCompilationCacheDownloader(context, git, remoteCache.getUrl(), remoteGitUrl, availableForHeadCommit,
                                                    jpsCaches.getSkipDownload());
    }

    public PortableCompilationCacheDownloader doCall() {
      return doCall(null);
    }
  });
  @Lazy private PortableCompilationCacheUploader uploader = new Closure<PortableCompilationCacheUploader>(this, this) {
    public PortableCompilationCacheUploader doCall(Object it) {
      String syncFolder = require(AWS_SYNC_FOLDER_PROPERTY, "AWS sync folder");
      String commitHash = require(COMMIT_HASH_PROPERTY, "Repository commit");
      context.getMessages().buildStatus(commitHash);
      return new PortableCompilationCacheUploader(context, remoteCache.getUploadUrl(), remoteGitUrl, commitHash, syncFolder,
                                                  jpsCaches.getSkipUpload(), forceRebuild);
    }

    public PortableCompilationCacheUploader doCall() {
      return doCall(null);
    }
  }.call(new Closure<PortableCompilationCacheUploader>(this, this) {
    public PortableCompilationCacheUploader doCall(Object it) {
      String syncFolder = require(AWS_SYNC_FOLDER_PROPERTY, "AWS sync folder");
      String commitHash = require(COMMIT_HASH_PROPERTY, "Repository commit");
      context.getMessages().buildStatus(commitHash);
      return new PortableCompilationCacheUploader(context, remoteCache.getUploadUrl(), remoteGitUrl, commitHash, syncFolder,
                                                  jpsCaches.getSkipUpload(), forceRebuild);
    }

    public PortableCompilationCacheUploader doCall() {
      return doCall(null);
    }
  });
  private static boolean isAlreadyUpdated;

  /**
   * JPS data structures allowing incremental compilation for {@link CompilationOutput}
   */
  public final class JpsCaches {
    public JpsCaches(PortableCompilationCache enclosing, CompilationContext context) {
      this.context = context;
    }

    public final boolean getSkipDownload() {
      return skipDownload;
    }

    public final boolean isSkipDownload() {
      return skipDownload;
    }

    public final boolean getSkipUpload() {
      return skipUpload;
    }

    public final boolean isSkipUpload() {
      return skipUpload;
    }

    public File getDir() {
      return dir;
    }

    public void setDir(File dir) {
      this.dir = dir;
    }

    public boolean getMaybeAvailableLocally() {
      return maybeAvailableLocally;
    }

    public boolean isMaybeAvailableLocally() {
      return maybeAvailableLocally;
    }

    public void setMaybeAvailableLocally(boolean maybeAvailableLocally) {
      this.maybeAvailableLocally = maybeAvailableLocally;
    }

    /**
     * {@link JpsCaches} archive upload may be skipped if only {@link CompilationOutput}s are required
     * without any incremental compilation (for tests execution as an example)
     */
    private static final String SKIP_UPLOAD_PROPERTY = "intellij.jps.remote.cache.uploadCompilationOutputsOnly";
    /**
     * {@link JpsCaches} archive download may be skipped if only {@link CompilationOutput}s are required
     * without any incremental compilation (for tests execution as an example)
     */
    private static final String SKIP_DOWNLOAD_PROPERTY = "intellij.jps.remote.cache.downloadCompilationOutputsOnly";
    private final CompilationContext context;
    private final boolean skipDownload = bool(SKIP_DOWNLOAD_PROPERTY, false);
    private final boolean skipUpload = bool(SKIP_UPLOAD_PROPERTY, false);
    @Lazy private File dir = new Closure<File>(this, this) {
      public File doCall(Object it) { return context.getCompilationData().getDataStorageRoot(); }

      public File doCall() {
        return doCall(null);
      }
    }.call(new Closure<File>(this, this) {
      public File doCall(Object it) { return context.getCompilationData().getDataStorageRoot(); }

      public File doCall() {
        return doCall(null);
      }
    });
    @Lazy private boolean maybeAvailableLocally = new Closure<Boolean>(this, this) {
      public Boolean doCall(Object it) {
        String[] files = getDir().list();
        context.getMessages().info(getDir().getAbsolutePath() + ": " + String.valueOf(files));
        return getDir().isDirectory() && files != null && files.length > 0;
      }

      public Boolean doCall() {
        return doCall(null);
      }
    }.call(new Closure<Boolean>(this, this) {
      public Boolean doCall(Object it) {
        String[] files = getDir().list();
        context.getMessages().info(getDir().getAbsolutePath() + ": " + String.valueOf(files));
        return getDir().isDirectory() && files != null && files.length > 0;
      }

      public Boolean doCall() {
        return doCall(null);
      }
    });
  }

  /**
   * Server which stores {@link PortableCompilationCache}
   */
  public final class RemoteCache {
    public static String getURL_PROPERTY() {
      return URL_PROPERTY;
    }

    public String getUrl() {
      return url;
    }

    public void setUrl(String url) {
      this.url = url;
    }

    public String getUploadUrl() {
      return uploadUrl;
    }

    public void setUploadUrl(String uploadUrl) {
      this.uploadUrl = uploadUrl;
    }

    /**
     * URL for read/write operations
     */
    private static final String UPLOAD_URL_PROPERTY = "intellij.jps.remote.cache.upload.url";
    /**
     * If true then {@link RemoteCache} is configured to be used
     */
    private static final boolean IS_CONFIGURED = !Strings.isEmptyOrSpaces(System.getProperty(RemoteCache.getURL_PROPERTY()));
    /**
     * URL for read-only operations
     */
    private static final String URL_PROPERTY = "intellij.jps.remote.cache.url";
    @Lazy private String url = new Closure<String>(this, this) {
      public String doCall(Object it) { return require(getURL_PROPERTY(), "Remote Cache url"); }

      public String doCall() {
        return doCall(null);
      }
    }.call(new Closure<String>(this, this) {
      public String doCall(Object it) { return require(getURL_PROPERTY(), "Remote Cache url"); }

      public String doCall() {
        return doCall(null);
      }
    });
    @Lazy private String uploadUrl = new Closure<String>(this, this) {
      public String doCall(Object it) { return require(UPLOAD_URL_PROPERTY, "Remote Cache upload url"); }

      public String doCall() {
        return doCall(null);
      }
    }.call(new Closure<String>(this, this) {
      public String doCall(Object it) { return require(UPLOAD_URL_PROPERTY, "Remote Cache upload url"); }

      public String doCall() {
        return doCall(null);
      }
    });
  }

  private static boolean setUseCompiledClassesFromProjectOutput(BuildOptions propOwner, boolean <set-?>) {
    propOwner.setUseCompiledClassesFromProjectOutput(getProperty("set") - ?: >);
    return <set - ?>;
  }
}
