package com.intellij.tools.build.bazel.jvmIncBuilder.impl;

import com.intellij.tools.build.bazel.jvmIncBuilder.BuildContext;
import com.intellij.tools.build.bazel.jvmIncBuilder.DataPaths;
import com.intellij.tools.build.bazel.jvmIncBuilder.Message;
import com.intellij.tools.build.bazel.jvmIncBuilder.NodeSourceSnapshotDelta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.dependency.NodeSource;
import org.jetbrains.jps.dependency.NodeSourcePathMapper;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.jetbrains.jps.util.Iterators.*;

public class BuildDiagnosticCollector {
  // keep history data for this period of time
  private static final long DATA_KEEP_THRESHOLD_SECONDS = 24 /*hours*/ * 60 * 60;
  private static final int MAX_BUILD_SESSIONS_TO_KEEP = 20;

  private final @NotNull BuildContext myContext;
  private final @NotNull Instant myStartTime = Instant.now();
  private @Nullable CompileRoundData myLibrariesDifferentiateLog;
  private @Nullable CompileRoundData mySourcesDifferentiateLog;
  private final ArrayList<CompileRoundData> myRounds = new ArrayList<>();

  private boolean myIsWholeTargetRebuild;
  private long myLibrariesDifferentiateBegin = -1L;
  private long myLibrariesDifferentiateEnd = -1L;

  public BuildDiagnosticCollector(@NotNull BuildContext context) {
    myContext = context;
    myIsWholeTargetRebuild = context.isRebuild();
  }

  public void setWholeTargetRebuild(boolean wholeTargetRebuild) {
    myIsWholeTargetRebuild = wholeTargetRebuild;
  }

  public void markLibrariesDifferentiateBegin() {
    myLibrariesDifferentiateBegin = System.nanoTime();
  }
  public void markLibrariesDifferentiateEnd() {
    myLibrariesDifferentiateEnd = System.nanoTime();
  }

  public void setLibrariesDifferentiateLog(Iterable<NodeSource> affectedSources, @Nullable String librariesDifferentiateLog) {
    myLibrariesDifferentiateLog = new CompileRoundData(affectedSources, librariesDifferentiateLog);
  }

  public void setSourcesDifferentiateLog(Iterable<NodeSource> affectedSources, @Nullable String sourcesDifferentiateLog) {
    mySourcesDifferentiateLog = new CompileRoundData(affectedSources, sourcesDifferentiateLog);
  }

  public void addRoundData(Iterable<NodeSource> compiledSources, String differentiateLog) {
    myRounds.add(new CompileRoundData(compiledSources, differentiateLog));
  }

  public void writeData(@Nullable ConfigurationState pastState, @Nullable ConfigurationState presentState) {
    try {
      Path readPath = DataPaths.getDiagnosticDataPath(myContext);
      Path writePath;
      if (Files.exists(readPath)) {
        writePath = readPath.resolveSibling(readPath.getFileName() + ".tmp");
      }
      else {
        writePath = readPath;
        readPath = null;
      }

      boolean hasErrors = myContext.hasErrors();
      if (readPath == null && !hasErrors && myIsWholeTargetRebuild) {
        return; // successful full rebuild without prior history: nothing to diagnose
      }

      String dataDir = "build-" + new SimpleDateFormat("dd-MM-yyyy-HH_mm_ss").format(new Date(myStartTime.toEpochMilli())) + (hasErrors? "-error" : "");
      // data to write
      // - deleted sources/libraries
      // - changed or added sources and their path and content
      // - changed or added libraries and their path

      try (var zos = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(writePath)))) {
        zos.setMethod(ZipOutputStream.DEFLATED);
        zos.setLevel(Deflater.BEST_SPEED);
        NodeSourcePathMapper pathMapper = myContext.getPathMapper();

        zos.putNextEntry(createZipEntry(dataDir, "description.txt"));
        //noinspection IOResourceOpenedButNotSafelyClosed
        PrintWriter readme = new PrintWriter(zos, true, StandardCharsets.UTF_8);

        readme.println(new SimpleDateFormat("dd-MM-yyyy HH:mm:ss").format(new Date(myStartTime.toEpochMilli())));

        if (hasErrors) {
          readme.println("Build completed with errors: ");
          for (Message error : myContext.getErrors()) {
            readme.println();
            readme.print(error.getText());
          }
        }

        readme.println();

        NodeSourceSnapshotDelta srcDelta;
        if (pastState != null && presentState != null) {
          var digestRenderer = new Object() {
            void formatDigest(String label, ConfigurationState past, ConfigurationState present, Function<? super ConfigurationState, Long> dataAccessor) {
              long pastValue = dataAccessor.apply(past);
              long presentValue = dataAccessor.apply(present);
              readme.format("%n%-20s digest %s => %s %s", label, Long.toHexString(pastValue), Long.toHexString(presentValue), (pastValue == presentValue ? "(unchanged)" : "(modified)"));
            }
          };
          digestRenderer.formatDigest("Worker Flags", pastState, presentState, ConfigurationState::getFlagsDigest);
          digestRenderer.formatDigest("Classpath Structure", pastState, presentState, ConfigurationState::getClasspathStructureDigest);
          digestRenderer.formatDigest("Runners", pastState, presentState, ConfigurationState::getRunnersDigest);
          digestRenderer.formatDigest("Untracked Inputs", pastState, presentState, ConfigurationState::getUntrackedInputsDigest);
          readme.println();
          readme.format("Whole target rebuild from the beginning? %s", myIsWholeTargetRebuild? "Yes" : "No");

          srcDelta = new SnapshotDeltaImpl(pastState.getSources(), presentState.getSources());
          writeSources(readme, "Deleted Sources:", srcDelta.getDeleted());
          writeSources(readme, "Changed Sources:", srcDelta.getChanged());
          writeSources(readme, "Added Sources:", filter(srcDelta.getModified(), s -> !contains(srcDelta.getChanged(), s)));

          NodeSourceSnapshotDelta libDelta = new SnapshotDeltaImpl(pastState.getLibraries(), presentState.getLibraries());
          writeSources(readme, "Deleted Binary Dependencies:", libDelta.getDeleted());
          writeSources(readme, "Changed Binary Dependencies:", libDelta.getChanged());
          writeSources(readme, "Added Binary Dependencies:", filter(libDelta.getModified(), s -> !contains(libDelta.getChanged(), s)));
        }
        else {
          srcDelta = null;
          readme.print("Past and/or present configuration state are not available");
          readme.format("Whole target rebuild from the beginning? %s", myIsWholeTargetRebuild? "Yes" : "No");
        }

        if (myLibrariesDifferentiateBegin > 0L && myLibrariesDifferentiateEnd > myLibrariesDifferentiateBegin) {
          readme.println();
          readme.format("Binary dependencies differentiate time: %s", Duration.ofNanos(myLibrariesDifferentiateEnd - myLibrariesDifferentiateBegin));
        }

        if (myLibrariesDifferentiateLog != null) {
          writeRoundData(readme, "Sources affected after binary dependencies differentiate:", 0, myLibrariesDifferentiateLog);
        }
        if (mySourcesDifferentiateLog != null) {
          writeRoundData(readme, "Sources affected after compile scope expansion differentiate:", 0, mySourcesDifferentiateLog);
        }
        for (int roundNumber = 0; roundNumber < myRounds.size(); roundNumber++) {
          writeRoundData(readme, "Sources compiled in round " + roundNumber + ":", roundNumber, myRounds.get(roundNumber));
        }

        readme.flush();

        Path outputArtifact = myContext.getOutputZip();
        String artifactFileName = outputArtifact.getFileName().toString();
        Predicate<Path> paramsFileFilter = p -> {
          String fname = p.getFileName().toString();
          return fname.startsWith(artifactFileName) && fname.endsWith(DataPaths.PARAMS_FILE_NAME_SUFFIX);
        };
        for (Path paramFile : Files.list(outputArtifact.getParent()).filter(paramsFileFilter).toList()) {
          try (InputStream in = Files.newInputStream(paramFile)) {
            zos.putNextEntry(createZipEntry(dataDir, paramFile.getFileName().toString()));
            in.transferTo(zos);
          }
        }

        if (!myIsWholeTargetRebuild && srcDelta != null) {
          // save contents of files compiled in this build session
          for (NodeSource src : unique(flat(srcDelta.getModified(), flat(map(myRounds, r -> r.sources))))) {
            try (InputStream in = Files.newInputStream(pathMapper.toPath(src))) {
              zos.putNextEntry(createZipEntry(dataDir, src.toString()));
              in.transferTo(zos);
            }
          }
        }

        // keep previous data for some period of time
        if (readPath != null) {
          try (var zis = new ZipInputStream(new BufferedInputStream(Files.newInputStream(readPath)))) {

            var entryCounter = new Object() {
              private final Set<String> processed = new HashSet<>(Set.of(dataDir));

              boolean shouldKeepEntry(ZipEntry entry) {
                if (!processed.add(getBuildSessionName(entry))) {
                  return true; // the entry is a part of a build session, which is kept
                }
                if (processed.size() > MAX_BUILD_SESSIONS_TO_KEEP) {
                  return false;
                }
                FileTime creation = entry.getCreationTime();
                return creation != null && Duration.between(creation.toInstant(), myStartTime).getSeconds() < DATA_KEEP_THRESHOLD_SECONDS;
              }
            };

            for (ZipEntry entry = zis.getNextEntry(); entry != null && entryCounter.shouldKeepEntry(entry); entry = zis.getNextEntry()) {
              zos.putNextEntry(entry);
              zis.transferTo(zos);
            }
          }
        }

      }
      finally {
        if (readPath != null) { // was writing into a temp file
          Files.move(writePath, readPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        }
      }

    }
    catch (Throwable e) {
      myContext.report(Message.info(null, "Could not save additional diagnostic: " + e.getMessage()));
    }
  }

  private static String getBuildSessionName(ZipEntry entry) {
    String name = entry.getName();
    int idx = name.indexOf("/");
    return idx >= 0? name.substring(0, idx) : name;
  }

  private static void writeRoundData(PrintWriter out, String header, int roundNumber, CompileRoundData round) {
    if (!writeSources(out, header, round.sources)) {
      out.println();
      out.println();
      out.print(header);
      out.print(" Nothing found");
    }
    if (!round.differentiateLog.isEmpty()) {
      out.println();
      out.printf("%n====================== IC decision trace for round %d ===============================%n", roundNumber);
      out.println(round.differentiateLog);
    }
  }

  private static boolean writeSources(PrintWriter out, String header, Iterable<@NotNull NodeSource> sources) {
    if (!isEmpty(sources)) {
      out.println();
      out.println();
      out.println(header);
      for (NodeSource source : sources) {
        out.println();
        out.print(source);
      }
      return true;
    }
    return false;
  }

  private @NotNull ZipEntry createZipEntry(String dirName, String entryName) {
    ZipEntry entry = new ZipEntry(dirName + "/" + entryName);
    entry.setCreationTime(FileTime.from(myStartTime));
    return entry;
  }

  private record CompileRoundData(Iterable<NodeSource> sources, String differentiateLog) {}
}
