import com.sun.management.HotSpotDiagnosticMXBean;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

public final class JarClassLoading {
  private static final int CLASS_COUNT = 32;

  public static void main(String[] args) throws Exception {
    if (args.length == 1 && args[0].equals("--help")) {
      System.out.println(
        "JarClassLoading [seconds=60] [load-class|for-name|implicit-only|explicit-only] [virtual|platform] [timeoutSeconds=10]");
      System.out.println("Use 0 seconds for continuous execution. Both scheduler limits default to 2.");
      return;
    }
    if (args.length > 4) {
      throw new IllegalArgumentException("Too many arguments. Use --help for the syntax.");
    }
    var seconds = args.length > 0 ? Long.parseLong(args[0]) : 60;
    var mode = args.length > 1 ? args[1] : "load-class";
    if (!Set.of("load-class", "for-name", "implicit-only", "explicit-only").contains(mode)) {
      throw new IllegalArgumentException("Unknown loading mode: " + mode);
    }
    var platform = switch (args.length > 2 ? args[2] : "virtual") {
      case "virtual" -> false;
      case "platform" -> true;
      default -> throw new IllegalArgumentException("The thread kind must be virtual or platform.");
    };
    var timeout = args.length > 3 ? Long.parseLong(args[3]) : 10;
    var carriers = Integer.parseInt(System.getProperty("jdk.virtualThreadScheduler.parallelism", "2"));
    var maximum = Integer.parseInt(System.getProperty("jdk.virtualThreadScheduler.maxPoolSize", Integer.toString(carriers)));
    if (seconds < 0 || timeout <= 0 || carriers <= 0 || maximum != carriers) {
      throw new IllegalArgumentException("Use a nonnegative duration, a positive timeout, and equal positive scheduler limits.");
    }
    System.setProperty("jdk.virtualThreadScheduler.parallelism", Integer.toString(carriers));
    System.setProperty("jdk.virtualThreadScheduler.maxPoolSize", Integer.toString(carriers));
    var directory = Files.createTempDirectory("jar-class-loading-");
    var jar = directory.resolve("payloads.jar");
    createJar(jar);
    System.out.printf("Runtime: %s %s; mode: %s; platform: %s; carriers: %d; PID: %d%n",
                      System.getProperty("java.vendor"), System.getProperty("java.runtime.version"), mode, platform,
                      carriers, ProcessHandle.current().pid());
    System.out.printf("OS: %s %s; seconds: %d; timeout: %d seconds%n",
                      System.getProperty("os.name"), System.getProperty("os.arch"), seconds, timeout);
    var start = System.nanoTime();
    var duration = Duration.ofSeconds(seconds).toNanos();
    var lastReport = start;
    long rounds = 0;
    try {
      while (seconds == 0 || System.nanoTime() - start < duration) {
        runRound(jar, mode, platform, rounds, timeout);
        rounds++;
        if (System.nanoTime() - lastReport >= TimeUnit.SECONDS.toNanos(10)) {
          System.out.printf("Completed %,d rounds%n", rounds);
          lastReport = System.nanoTime();
        }
      }
      System.out.printf("PASS: %,d rounds%n", rounds);
    }
    catch (Exception | AssertionError failure) {
      System.err.println("Failure artifacts: " + directory);
      throw failure;
    }
    Files.delete(jar);
    Files.delete(directory);
  }

  private static void runRound(Path jar, String mode, boolean platform, long round, long timeout) throws Exception {
    var loader = new URLClassLoader(new URL[]{jar.toUri().toURL()}, ClassLoader.getPlatformClassLoader());
    var tasks = new ArrayList<Runnable>();
    for (int index = 0; index < CLASS_COUNT; index++) {
      var constructor = loader.loadClass(name("Trigger", index)).getDeclaredConstructor();
      constructor.setAccessible(true);
      var implicit = (Runnable)constructor.newInstance();
      var target = name("Payload", index);
      Runnable explicit = () -> {
        try {
          var loaded = mode.equals("for-name") ? Class.forName(target, false, loader) : loader.loadClass(target);
          if (loaded.getClassLoader() != loader) {
            throw new AssertionError("The parent loader defined the payload.");
          }
        }
        catch (ClassNotFoundException exception) {
          throw new AssertionError(exception);
        }
      };
      tasks.add(mode.equals("implicit-only") ? implicit : explicit);
      tasks.add(mode.equals("explicit-only") ? explicit : implicit);
    }
    Collections.shuffle(tasks, new Random(round));
    var finished = new CountDownLatch(tasks.size());
    var failure = new AtomicReference<Throwable>();
    var threads = new ArrayList<Thread>();
    for (int index = 0; index < tasks.size(); index++) {
      var task = tasks.get(index);
      Thread.Builder builder = platform ? Thread.ofPlatform().daemon() : Thread.ofVirtual();
      threads.add(builder.name("loader-" + index).start(() -> {
        try {
          task.run();
        }
        catch (Throwable exception) {
          failure.compareAndSet(null, exception);
        }
        finally {
          finished.countDown();
        }
      }));
    }
    if (!finished.await(timeout, TimeUnit.SECONDS)) {
      System.err.printf("TIMEOUT: round %d; unfinished workers: %d%n", round, finished.getCount());
      var dump = jar.resolveSibling("threads.txt");
      try {
        ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class).dumpThreads(
          dump.toString(), HotSpotDiagnosticMXBean.ThreadDumpFormat.TEXT_PLAIN);
        System.err.println("Thread dump: " + dump);
      }
      catch (Exception exception) {
        System.err.println("Cannot write the thread dump: " + exception);
      }
      for (var thread : threads) {
        if (thread.isAlive()) {
          var stack = thread.getStackTrace();
          if (Arrays.stream(stack).noneMatch(frame -> frame.getClassName().equals("java.lang.ClassLoader") ||
                                                      frame.getClassName().equals("java.util.zip.ZipFile"))) {
            continue;
          }
          System.err.println(thread + " " + thread.getState());
          for (var frame : stack) {
            System.err.println("    at " + frame);
          }
        }
      }
      throw new AssertionError("Class loading did not complete.");
    }
    loader.close();
    if (failure.get() != null) {
      throw new AssertionError("A worker failed.", failure.get());
    }
  }

  private static String name(String prefix, int index) {
    return prefix + String.format("%03d", index);
  }

  private static byte[] template(String name) throws IOException {
    try (var stream = JarClassLoading.class.getResourceAsStream("/" + name + ".class")) {
      if (stream == null) {
        throw new IOException("Compile this file with javac: " + name);
      }
      return stream.readAllBytes();
    }
  }

  private static byte[] rename(byte[] template, int index) {
    var bytes = template.clone();
    for (var prefix : new String[]{"Trigger", "Payload"}) {
      var original = (prefix + "000").getBytes(StandardCharsets.UTF_8);
      var replacement = name(prefix, index).getBytes(StandardCharsets.UTF_8);
      for (int offset = 0; offset <= bytes.length - original.length; offset++) {
        if (Arrays.equals(bytes, offset, offset + original.length, original, 0, original.length)) {
          System.arraycopy(replacement, 0, bytes, offset, replacement.length);
        }
      }
    }
    return bytes;
  }

  private static void createJar(Path path) throws IOException {
    var manifest = new Manifest();
    manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
    manifest.getMainAttributes().put(Attributes.Name.MULTI_RELEASE, "true");
    var trigger = template("Trigger000");
    var payload = template("Payload000");
    try (var output = new JarOutputStream(Files.newOutputStream(path), manifest)) {
      for (int index = 0; index < CLASS_COUNT; index++) {
        output.putNextEntry(new JarEntry(name("Trigger", index) + ".class"));
        output.write(rename(trigger, index));
        output.closeEntry();
        for (var prefix : new String[]{"", "META-INF/versions/21/"}) {
          output.putNextEntry(new JarEntry(prefix + name("Payload", index) + ".class"));
          output.write(rename(payload, index));
          output.closeEntry();
        }
      }
    }
  }
}

final class Trigger000 implements Runnable {
  @Override
  public void run() {
    if (new Payload000().value() != 42) {
      throw new AssertionError("Incorrect payload.");
    }
  }
}

final class Payload000 {
  int value() {
    return 42;
  }
}
