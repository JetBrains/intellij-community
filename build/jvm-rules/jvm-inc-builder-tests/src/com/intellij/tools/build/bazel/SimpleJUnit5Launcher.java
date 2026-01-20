package com.intellij.tools.build.bazel;

import com.intellij.tools.build.bazel.impl.BazelIncBuildTest;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;
import org.junit.platform.reporting.legacy.xml.LegacyXmlReportGeneratingListener;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import com.google.devtools.build.runfiles.Runfiles;

public final class SimpleJUnit5Launcher {
  private static final int EXIT_CODE_SUCCESS = 0;
  private static final int EXIT_CODE_ERROR = 1;

  public static void main(String[] args) throws IOException {
    if (args.length == 0) {
      System.err.println("ERROR: No test class provided. Usage: SimpleJUnit5Launcher <fully.qualified.TestClass>");
      System.exit(EXIT_CODE_ERROR);
    }
    final String testClassName = args[0];

    Runfiles runfiles = Runfiles.create();

    String bazelExecRlocation = System.getProperty(BazelIncBuildTest.BAZEL_EXECUTABLE);
    String bazelExecutable = bazelExecRlocation != null? runfiles.rlocation(bazelExecRlocation) : null;
    if (bazelExecutable == null || bazelExecutable.isEmpty()) {
      System.err.println("ERROR: Path to bazel executable is not specified. Please check system variable \"" + BazelIncBuildTest.BAZEL_EXECUTABLE + "\" is correctly set");
      System.exit(EXIT_CODE_ERROR);
    }
    System.setProperty(BazelIncBuildTest.BAZEL_EXECUTABLE, bazelExecutable); // the test expects absolute path

    String testWorkspace = runfiles.rlocation("rules_jvm+/jvm-inc-builder-tests/testData/MODULE.bazel.txt");
    if (testWorkspace == null || testWorkspace.isEmpty()) {
      System.err.println("ERROR: Cannot find \"rules_jvm+/MODULE.bazel.txt\" workspace file. This runner is designed for Bazel.");
      System.exit(EXIT_CODE_ERROR);
    }

    Path modulePath = Path.of(testWorkspace);
    if (!Files.exists(modulePath)) {
      System.err.println("ERROR: Test data workspace file \"" + modulePath + "\" does not exist");
      System.exit(EXIT_CODE_ERROR);
    }

    // configure the workspace root property required by tests
    Path wsPath = modulePath.toRealPath().getParent().getParent().getParent();
    System.setProperty(BazelIncBuildTest.WORKSPACE_ROOT_PROPERTY, wsPath.toString().replace(File.separatorChar, '/'));

    final String xmlOutputFile = System.getenv("XML_OUTPUT_FILE");
    if (xmlOutputFile == null || xmlOutputFile.isEmpty()) {
      System.err.println("ERROR: XML_OUTPUT_FILE environment variable not set. This runner is designed for Bazel.");
      System.exit(EXIT_CODE_ERROR);
    }

    Path reportPath = Path.of(xmlOutputFile);

    LauncherDiscoveryRequest request;
    try {
      request = LauncherDiscoveryRequestBuilder.request().selectors(
        DiscoverySelectors.selectClass(Class.forName(testClassName))
      ).build();
    }
    catch (ClassNotFoundException e) {
      System.err.println("ERROR: Test class not found: " + testClassName);
      e.printStackTrace(System.err);
      System.exit(EXIT_CODE_ERROR);
      return; // For the compiler
    }

    final SummaryGeneratingListener summaryListener = new SummaryGeneratingListener();
    try  {
      Path tempDirectory = Files.createTempDirectory("simple-junit5-launcher-");
      LegacyXmlReportGeneratingListener xmlListener = new LegacyXmlReportGeneratingListener(tempDirectory, new PrintWriter(System.err));
      Launcher launcher = LauncherFactory.create();
      launcher.registerTestExecutionListeners(summaryListener, xmlListener);
      launcher.execute(request);

      Path report = Files.list(tempDirectory).filter(p -> p.getFileName().toString().endsWith(".xml")).findFirst().orElse(null);
      Files.move(report, reportPath, StandardCopyOption.REPLACE_EXISTING);
      Files.delete(tempDirectory);
    }
    catch (IOException e) {
      e.printStackTrace(System.err);
      System.exit(EXIT_CODE_ERROR);
    }

    TestExecutionSummary summary = summaryListener.getSummary();
    System.exit(summary.getTotalFailureCount() == 0? EXIT_CODE_SUCCESS : EXIT_CODE_ERROR);
  }
}