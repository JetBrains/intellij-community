// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import com.google.gson.Gson
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.Trinity
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.StreamUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.io.Compressor
import com.intellij.util.io.Decompressor
import groovy.transform.CompileStatic
import org.apache.http.HttpStatus
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpHead
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.log4j.AppenderSkeleton
import org.apache.log4j.Level
import org.apache.log4j.Logger
import org.apache.log4j.PatternLayout
import org.apache.log4j.spi.LoggingEvent
import org.apache.tools.ant.BuildException
import org.apache.tools.ant.taskdefs.ExecTask
import org.jetbrains.annotations.NotNull
import org.jetbrains.intellij.build.BuildMessages
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.impl.logging.IntelliJBuildException

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

@CompileStatic
class CompilationPartsUtil {

  static void initLog4J(BuildMessages messages) {
    def logger = Logger.getRootLogger()
    logger.setLevel(Level.INFO)
    if (logger.allAppenders.hasMoreElements()) {
      messages.
        warning("Will override existing log4j appenders: ${logger.allAppenders.iterator().collect { it -> it.toString() }.join(",")}")
      logger.removeAllAppenders()
    }
    logger.addAppender(new AppenderSkeleton() {
      {
        setLayout(new PatternLayout("[%c] %m"))
      }

      @Override
      protected void append(LoggingEvent event) {
        def level = event.getLevel()
        String message = this.getLayout().format(event)
        if (level.isGreaterOrEqual(Level.ERROR)) {
          def throwable = event.throwableInformation?.throwable
          if (throwable != null) {
            messages.error(message, throwable)
          }
          else {
            messages.error(message)
          }
          return
        }
        if (level.isGreaterOrEqual(Level.WARN)) {
          messages.warning(message)
          return
        }
        if (level.isGreaterOrEqual(Level.INFO)) {
          messages.info(message)
          return
        }
        if (level.isGreaterOrEqual(Level.DEBUG)) {
          messages.debug(message)
          return
        }
        messages.warning("Unsupported log4j level: $level")
        messages.info(message)
      }

      @Override
      void close() {
      }

      @Override
      boolean requiresLayout() {
        return false
      }
    })
  }

  static void deinitLog4J() {
    def logger = Logger.getRootLogger()
    logger.setLevel(Level.DEBUG)
    logger.removeAllAppenders()
  }

  static void packAndUploadToServer(CompilationContextImpl context, String zipsLocation) {
    BuildMessages messages = context.messages

    String serverUrl = System.getProperty("intellij.build.compiled.classes.server.url")
    if (StringUtil.isEmptyOrSpaces(serverUrl)) {
      messages.warning("Compile Parts archive server url is not defined. \n" +
                       "Will not upload to remote server. Please set 'intellij.compile.archive.url' system property.")
      return
    }
    String intellijCompileArtifactsBranchProperty = 'intellij.build.compiled.classes.branch'
    String branch = System.getProperty(intellijCompileArtifactsBranchProperty)
    if (StringUtil.isEmptyOrSpaces(branch)) {
      messages.warning("Unable to determine current git branch, assuming 'master'. \n" +
                       "Please set '$intellijCompileArtifactsBranchProperty' system property")
      branch = 'master'
    }

    //region Prepare executor
    int executorThreadsCount = Runtime.getRuntime().availableProcessors()
    messages.info("Will use up to $executorThreadsCount threads for packing and uploading")

    def executor = new NamedThreadPoolExecutor('Compile Parts', executorThreadsCount)
    executor.prestartAllCoreThreads()
    //endregion

    def incremental = context.options.incrementalCompilation
    if (!incremental) {
      FileUtil.delete(new File(zipsLocation))
    }
    FileUtil.ensureExists(new File(zipsLocation))

    Map<String, String> hashes = new ConcurrentHashMap<String, String>(2048)
    List<PackAndUploadContext> contexts = new ArrayList<PackAndUploadContext>(2048)

    File root = context.getProjectOutputDirectory().getAbsoluteFile()
    List<File> subroots = root.listFiles().toList().collect { it.absoluteFile } // production, test
    for (File subroot : subroots) {
      FileUtil.ensureExists(new File("$zipsLocation/${subroot.name}"))

      def modules = subroot.listFiles().toList().collect { it.absoluteFile }
      for (File module : modules) {
        def files = module.list()
        if (files == null || files.size() == 0) {
          // Skip empty directories
          continue
        }
        String name = "${subroot.name}/${module.name}".toString()
        def ctx = new PackAndUploadContext(module, name, "$zipsLocation/${name}.jar".toString())
        contexts.add(ctx)
      }
    }

    messages.block("Building zip archives") {
      runUnderStatisticsTimer(messages, 'compile-parts:pack:time') {
        contexts.each { PackAndUploadContext ctx ->
          executor.submit {
            pack(messages, context.ant, ctx, incremental)
          }
        }

        executor.waitForAllComplete(messages)
      }
    }

    executor.reportErrors(messages)

    // TODO: Remove hardcoded constant
    String uploadPrefix = "intellij-compile/v1/$branch".toString()

    messages.block("Compute archives checksums") {
      runUnderStatisticsTimer(messages, 'compile-parts:checksum:time') {
        contexts.each { PackAndUploadContext ctx ->
          executor.submit {
            String hash = computeHash(new File(ctx.archive))
            hashes.put(ctx.name, hash)
          }
        }
        executor.waitForAllComplete(messages)
      }
    }

    // Prepare metadata for writing into file
    CompilationPartsMetadata m = new CompilationPartsMetadata()
    m.serverUrl = serverUrl
    m.branch = branch
    m.prefix = uploadPrefix
    m.files = new TreeMap<String, String>(hashes)
    String metadataJson = new Gson().toJson(m)

    messages.block("Uploading archives") {
      AtomicInteger uploadedCount = new AtomicInteger()
      AtomicLong uploadedBytes = new AtomicLong()
      AtomicInteger reusedCount = new AtomicInteger()
      AtomicLong reusedBytes = new AtomicLong()

      runUnderStatisticsTimer(messages, 'compile-parts:upload:time') {
        CompilationPartsUploader uploader = new CompilationPartsUploader(serverUrl, messages)

        Set<String> alreadyUploaded = new HashSet<>()
        boolean fallbackToHeads
        def files = uploader.getFoundAndMissingFiles(metadataJson)
        if (files != null) {
          messages.info("Successfully fetched info about already uploaded files")
          alreadyUploaded.addAll(files.found)
          fallbackToHeads = false
        }
        else {
          messages.warning("Failed to fetch info about already uploaded files, will fallback to HEAD requests")
          fallbackToHeads = true
        }

        // Upload with higher threads count
        executor.setMaximumPoolSize(executorThreadsCount * 2)
        executor.prestartAllCoreThreads()

        contexts.each { PackAndUploadContext ctx ->
          if (alreadyUploaded.contains(ctx.name)) {
            reusedCount.getAndIncrement()
            reusedBytes.getAndAdd(new File(ctx.archive).size())
            return
          }

          executor.submit {
            def archiveFile = new File(ctx.archive)

            String hash = hashes.get(ctx.name)
            def path = "$uploadPrefix/${ctx.name}/${hash}.jar".toString()

            if (uploader.upload(path, archiveFile, fallbackToHeads)) {
              uploadedCount.getAndIncrement()
              uploadedBytes.getAndAdd(archiveFile.size())
            }
            else {
              reusedCount.getAndIncrement()
              reusedBytes.getAndAdd(archiveFile.size())
            }
          }
        }

        executor.waitForAllComplete(messages)

        StreamUtil.closeStream(uploader)
      }

      messages.info("Upload complete: reused ${reusedCount.get()} parts, uploaded ${uploadedCount.get()} parts")
      messages.reportStatisticValue('compile-parts:reused:bytes', reusedBytes.get().toString())
      messages.reportStatisticValue('compile-parts:reused:count', reusedCount.get().toString())
      messages.reportStatisticValue('compile-parts:uploaded:bytes', uploadedBytes.get().toString())
      messages.reportStatisticValue('compile-parts:uploaded:count', uploadedCount.get().toString())
      messages.reportStatisticValue('compile-parts:total:bytes', (reusedBytes.get() + uploadedBytes.get()).toString())
      messages.reportStatisticValue('compile-parts:total:count', (reusedCount.get() + uploadedCount.get()).toString())
    }

    executor.close()

    executor.reportErrors(messages)


    // Save and publish metadata file
    def metadataFile = new File("$zipsLocation/metadata.json")
    FileUtil.writeToFile(metadataFile, metadataJson)

    messages.artifactBuilt(metadataFile.absolutePath)
  }

  static void fetchAndUnpackCompiledClasses(BuildMessages messages, String classesOutput, BuildOptions options) {
    def metadataFile = new File(options.pathToCompiledClassesArchivesMetadata)
    if (!metadataFile.isFile()) {
      messages.error("Cannot fetch compiled classes: metadata file not found at '$options.pathToCompiledClassesArchivesMetadata'")
      return
    }
    CompilationPartsMetadata metadata
    try {
      metadata = new Gson().fromJson(FileUtil.loadFile(metadataFile, CharsetToolkit.UTF8),
                                     CompilationPartsMetadata.class)
    }
    catch (Exception e) {
      messages.error("Failed to parse metadata file content: $e.message", e)
      return
    }
    String persistentCache = System.getProperty('agent.persistent.cache')
    String cache = persistentCache ?: new File(classesOutput).parentFile.getAbsolutePath()
    File tempDownloadsStorage = new File(cache, 'idea-compile-parts')

    Set<String> upToDate = ContainerUtil.newConcurrentSet()

    List<FetchAndUnpackContext> contexts = new ArrayList<FetchAndUnpackContext>(metadata.files.size())
    new TreeMap<String, String>(metadata.files).each { entry ->
      contexts.add(new FetchAndUnpackContext(entry.key, entry.value, new File("$classesOutput/$entry.key")))
    }

    //region Prepare executor
    int executorThreadsCount = Runtime.getRuntime().availableProcessors()
    messages.info("Will use up to $executorThreadsCount threads for downloading, verifying and unpacking")

    def executor = new NamedThreadPoolExecutor('Compile Parts', executorThreadsCount)
    executor.prestartAllCoreThreads()
    //endregion

    long verifyTime = 0l

    messages.block("Check previously unpacked directories") {
      long start = System.nanoTime()
      contexts.each { ctx ->
        def out = ctx.output
        if (!out.exists()) return
        executor.submit {
          if (out.isDirectory()) {
            def hashFile = new File(out, ".hash")
            if (hashFile.exists() && hashFile.isFile()) {
              try {
                String actual = FileUtil.loadFile(hashFile, StandardCharsets.UTF_8)
                if (actual == ctx.hash) {
                  upToDate.add(ctx.name)
                  return
                }
                else {
                  messages.info("Output directory '$ctx.name' hash mismatch, expected '$ctx.hash', got '$actual'")
                }
              }
              catch (Throwable e) {
                messages.warning("Output directory '$ctx.name' hash calculation failed: $e.message")
              }
            }
            else {
              messages.info("There's no .hash file in output directory '$ctx.name'")
            }
          }
          FileUtil.delete(out)
          return
        }
      }
      executor.submit {
        // Remove stalled directories not present in metadata
        def expectedDirectories = new HashSet<String>(metadata.files.keySet())
        // We need to traverse with depth 2 since first level is [production,test]
        def subroots = (new File(classesOutput).listFiles() ?: new File[0]).toList().findAll { it.directory }.collect { it.absoluteFile }
        for (File subroot : subroots) {
          def modules = subroot.listFiles()
          if (modules == null) continue
          for (File module : modules) {
            def name = "$subroot.name/$module.name".toString()
            if (!expectedDirectories.contains(name)) {
              messages.info("Removing stalled directory '$name'")
              FileUtil.delete(module)
            }
          }
        }
      }
      executor.waitForAllComplete(messages)
      verifyTime += TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)
    }

    messages.reportStatisticValue('compile-parts:up-to-date:count', upToDate.size().toString())
    executor.reportErrors(messages)

    List<FetchAndUnpackContext> toUnpack = new ArrayList<FetchAndUnpackContext>(contexts.size())
    Deque<FetchAndUnpackContext> toDownload = new ConcurrentLinkedDeque<FetchAndUnpackContext>()

    messages.block("Check previously downloaded archives") {
      long start = System.nanoTime()
      contexts.each { ctx ->
        if (upToDate.contains(ctx.name)) return
        toUnpack.add(ctx)
        executor.submit {
          ctx.jar = new File(tempDownloadsStorage, "${ctx.name}/${ctx.hash}.jar")
          def file = ctx.jar
          if (file.exists() && ctx.hash != computeHash(file)) {
            messages.info("File $file has unexpected hash, will refetch")
            FileUtil.delete(file)
          }
          if (!file.exists()) {
            toDownload.add(ctx)
          }
          return
        }
      }
      executor.waitForAllComplete(messages)
      verifyTime += TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)

      executor.reportErrors(messages)
    }


    messages.block("Fetch compiled classes archives") {
      long start = System.nanoTime()

      String prefix = metadata.prefix
      String serverUrl = metadata.serverUrl

      if (!toDownload.isEmpty()) {
        initLog4J(messages)

        def httpClient = HttpClientBuilder.create()
          .setUserAgent('Parts Downloader')
          .setMaxConnTotal(20)
          .setMaxConnPerRoute(10)
          .build()

        String urlWithPrefix = "$serverUrl/$prefix/".toString()

        // First let's check for initial redirect (mirror selection)
        messages.block("Mirror selection") {
          def head = new HttpHead(urlWithPrefix)
          head.setConfig(RequestConfig.custom().setRedirectsEnabled(false).build())
          httpClient.execute(head).withCloseable { response ->
            int statusCode = response.getStatusLine().getStatusCode()
            def locationHeader = response.getFirstHeader("location")
            if ((statusCode == HttpStatus.SC_MOVED_TEMPORARILY ||
                 statusCode == HttpStatus.SC_MOVED_PERMANENTLY ||
                 statusCode == HttpStatus.SC_TEMPORARY_REDIRECT ||
                 statusCode == HttpStatus.SC_SEE_OTHER)
              && locationHeader != null) {
              urlWithPrefix = locationHeader.getValue()
              messages.info("Redirected to mirror: " + urlWithPrefix)
            }
            else {
              messages.info("Will use origin server: " + urlWithPrefix)
            }
          }
        }

        toDownload.each { ctx ->
          executor.submit {
            FileUtil.ensureExists(ctx.jar.parentFile)
            def get = new HttpGet("${urlWithPrefix}${ctx.name}/${ctx.jar.name}")
            httpClient.execute(get).withCloseable { response ->
              if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
                messages.warning("Failed to fetch '${ctx.name}/${ctx.jar.name}', status code: ${response.getStatusLine().getStatusCode()}")
              }
              else {
                new BufferedInputStream(response.getEntity().getContent()).withCloseable { bis ->
                  new BufferedOutputStream(new FileOutputStream(ctx.jar)).withCloseable { bos ->
                    FileUtil.copy(bis, bos)
                  }
                }
              }
            }
          }
        }
        executor.waitForAllComplete(messages)

        StreamUtil.closeStream(httpClient)

        deinitLog4J()
      }

      messages.reportStatisticValue('compile-parts:download:time',
                                    TimeUnit.NANOSECONDS.toMillis((System.nanoTime() - start)).toString())

      long downloadedBytes = toDownload.collect { it.jar.size() }.sum(0l) as long

      messages.reportStatisticValue('compile-parts:downloaded:bytes', downloadedBytes.toString())
      messages.reportStatisticValue('compile-parts:downloaded:count', toDownload.size().toString())

      executor.reportErrors(messages)
    }

    messages.block("Verify downloaded archives") {
      long start = System.nanoTime()
      // todo: retry download if hash verification failed
      Set<Trinity<File, String, String>> failed = ContainerUtil.newConcurrentSet()

      toDownload.each { ctx ->
        executor.submit {
          def computed = computeHash(ctx.jar)
          def expected = ctx.hash
          if (expected != computed) {
            messages.warning("Downloaded file '$ctx.jar' hash mismatch, expected '$expected', got $computed")
            failed.add(Trinity.create(ctx.jar, expected, computed))
          }
          return
        }
      }
      executor.waitForAllComplete(messages)

      verifyTime += TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)
      messages.reportStatisticValue('compile-parts:verify:time', verifyTime.toString())
      if (!failed.isEmpty()) {
        failed.each { trinity ->
          messages.warning("Downloaded file '$trinity.first' hash mismatch, expected '$trinity.second', got '$trinity.third'")
        }
        messages.error("Hash mismatch for ${failed.size()} downloaded files, see details above")
      }

      executor.reportErrors(messages)
    }

    messages.block("Unpack compiled classes archives") {
      long start = System.nanoTime()
      toUnpack.each { ctx ->
        executor.submit {
          unpack(messages, ctx)
        }
      }
      executor.waitForAllComplete(messages)

      messages.reportStatisticValue('compile-parts:unpacked:bytes', toUnpack.collect { it.jar.size() }.sum(0l).toString())
      messages.reportStatisticValue('compile-parts:unpacked:count', toUnpack.size().toString())
      messages.reportStatisticValue('compile-parts:unpack:time',
                                    TimeUnit.NANOSECONDS.toMillis((System.nanoTime() - start)).toString())

      executor.reportErrors(messages)
    }

    executor.close()

    executor.reportErrors(messages)
  }

  private static void unpack(BuildMessages messages, FetchAndUnpackContext ctx) {
    messages.block("Unpacking $ctx.name") {
      FileUtil.ensureExists(ctx.output)
      new Decompressor.Zip(ctx.jar).overwrite(true).extract(ctx.output)
      // Save actual hash
      FileUtil.writeToFile(new File(ctx.output, ".hash"), ctx.hash)
    }
  }

  private static void pack(BuildMessages messages, AntBuilder ant, PackAndUploadContext ctx, boolean incremental) {
    messages.block("Packing $ctx.name") {
      if (SystemInfo.isUnix) {
        def task = new ExecTask()
        task.project = ant.project
        task.executable = "zip"
        task.dir = new File(ctx.output.absolutePath)
        task.createArg().line = "-1 -r -q"
        if (incremental) {
          task.createArg().line = "--filesync"
        }
        task.createArg().line = ctx.archive
        task.createArg().value = '.'
        task.execute()
      }
      else {
        def zip = new Compressor.Zip(new File(ctx.archive)).withLevel(1)
        zip.addDirectory(new File(ctx.output.absolutePath))
        zip.close()
      }
    }
  }

  private static String computeHash(File file) {
    if (file == null || !file.exists()) return null
    MessageDigest messageDigest = MessageDigest.getInstance("SHA-256")
    def fis = new FileInputStream(file)
    try {
      FileUtil.copy(fis, new DigestOutputStream(messageDigest))
      def digest = messageDigest.digest()
      def hex = StringUtil.toHexString(digest)
      return hex
    }
    finally {
      fis.close()
    }
  }

  private static class DigestOutputStream extends OutputStream {
    private final MessageDigest myDigest

    DigestOutputStream(MessageDigest digest) {
      this.myDigest = digest
    }

    @Override
    void write(int b) throws IOException {
      myDigest.update(b as byte)
    }

    @Override
    void write(@NotNull byte[] b, int off, int len) throws IOException {
      myDigest.update(b, off, len)
    }

    @Override
    String toString() {
      return "[Digest Output Stream] $myDigest"
    }
  }

  private static class PackAndUploadContext {
    final File output
    final String archive
    final String name

    PackAndUploadContext(File output, String name, String archive) {
      this.output = output
      this.archive = archive
      this.name = name
    }
  }

  private static class FetchAndUnpackContext {
    final String name
    final String hash
    final File output

    File jar

    FetchAndUnpackContext(String name, String hash, File output) {
      this.name = name
      this.hash = hash
      this.output = output
    }
  }

  private static class NamedThreadPoolExecutor extends ThreadPoolExecutor {
    private final AtomicInteger counter = new AtomicInteger()
    private final List<Future> futures = new LinkedList<Future>()
    private final ConcurrentLinkedDeque<Throwable> errors = new ConcurrentLinkedDeque<Throwable>()

    NamedThreadPoolExecutor(String threadNamePrefix, int maximumPoolSize) {
      super(1, maximumPoolSize, 1, TimeUnit.MINUTES, new LinkedBlockingDeque(2048))
      setThreadFactory(new ThreadFactory() {
        @NotNull
        @Override
        Thread newThread(@NotNull Runnable r) {
          Thread thread = new Thread(r, threadNamePrefix + ' ' + counter.incrementAndGet())
          thread.setPriority(Thread.NORM_PRIORITY - 1)
          return thread
        }
      })
    }

    void close() {
      shutdown()
      awaitTermination(10, TimeUnit.SECONDS)
      shutdownNow()
    }

    void submit(Closure<?> block) {
      futures.add(this.submit(new Runnable() {
        @Override
        void run() {
          try {
            block()
          }
          catch (Throwable e) {
            errors.add(e)
          }
        }
      }))
    }

    boolean reportErrors(BuildMessages messages) {
      if (!errors.isEmpty()) {
        messages.warning("Several (${errors.size()}) errors occured:")
        errors.each { Throwable t ->
          def writer = new StringWriter()
          new PrintWriter(writer).withCloseable { t?.printStackTrace(it) }
          messages.warning("${t.message}\n$writer" )
        }
        messages.error("Several (${errors.size()}) errors occured, see above")
        return true
      }
      return false
    }

    void waitForAllComplete(BuildMessages messages) {
      while (!futures.isEmpty()) {
        def iterator = futures.listIterator()
        while (iterator.hasNext()) {
          Future f = iterator.next()
          if (f.isDone()) {
            iterator.remove()
          }
        }
        if (futures.isEmpty()) break
        messages.info("${futures.size()} tasks left...")
        if (futures.size() < 100) {
          futures.last().get()
        }
        else {
          Thread.sleep(TimeUnit.SECONDS.toMillis(1))
        }
      }
    }
  }

  // based on org.jetbrains.intellij.build.impl.logging.BuildMessagesImpl.block
  private static <V> V runUnderStatisticsTimer(BuildMessages messages, String name, Closure<V> body) {
    def start = System.nanoTime()
    try {
      return body()
    }
    catch (IntelliJBuildException e) {
      throw e
    }
    catch (BuildException e) {
      throw new IntelliJBuildException(name, e.message, e.cause)
    }
    finally {
      def time = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)
      messages.reportStatisticValue(name, time.toString())
    }
  }
}
