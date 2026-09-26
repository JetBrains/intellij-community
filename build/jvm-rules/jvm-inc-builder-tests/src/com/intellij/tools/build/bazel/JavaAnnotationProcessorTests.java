package com.intellij.tools.build.bazel;

import com.intellij.tools.build.bazel.impl.BazelIncBuildTest;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Scenarios where java sources are handled by an annotation processor modelling JMH's output behavior
 * (see testData mockapt.MockBenchProcessor): a companion source file is generated per contributing class
 * with the class passed as the originating element, and two aggregate resources are written in the final round
 * with no originating elements — META-INF/MockList is merged with the previously existing content
 * (like JMH's BenchmarkList), META-INF/MockHints is overwritten with the current session's entries only
 * (like JMH's CompilerHints).
 * Besides the standard build-log and graph-vs-jar checks performed by the base class, these tests compare
 * per-entry content of the output jar across build steps to make sure that outputs generated for up-to-date
 * sources are preserved exactly, while outputs generated for changed sources are properly regenerated.
 */
public class JavaAnnotationProcessorTests extends BazelIncBuildTest {

  private static final String MOCK_LIST = "META-INF/MockList";
  private static final String MOCK_HINTS = "META-INF/MockHints";
  private static final String MARK_LIST = "META-INF/MarkList";

  private record JarSnapshot(String jarName, Map<String, String> entryDigests, Map<String, String> metaInfContent) {}

  /** Per-build snapshots of the output jars: entry name -> SHA-256 of entry content, plus text content of META-INF resources. */
  private final List<JarSnapshot> myJarSnapshots = new ArrayList<>();

  @Override
  protected void validateOutputArtifacts(BuildOutput output) throws IOException {
    Map<String, String> digests = new TreeMap<>();
    Map<String, String> metaInf = new TreeMap<>();
    try (ZipFile zip = new ZipFile(output.outputJar().toFile())) {
      Enumeration<? extends ZipEntry> entries = zip.entries();
      while (entries.hasMoreElements()) {
        ZipEntry entry = entries.nextElement();
        if (!entry.isDirectory()) {
          try (InputStream in = zip.getInputStream(entry)) {
            byte[] content = in.readAllBytes();
            digests.put(entry.getName(), sha256(content));
            if (entry.getName().startsWith("META-INF/") && !entry.getName().endsWith(".class")) {
              metaInf.put(entry.getName(), new String(content, StandardCharsets.UTF_8));
            }
          }
        }
      }
    }
    myJarSnapshots.add(new JarSnapshot(getFileName(output.outputJar()), digests, metaInf));
  }

  /** Snapshots of the given jar in build order; the scenarios build the processor jar and the "a" jar, only the latter is asserted on. */
  private List<JarSnapshot> snapshots(String jarName) {
    List<JarSnapshot> result = new ArrayList<>();
    for (JarSnapshot snapshot : myJarSnapshots) {
      if (jarName.equals(snapshot.jarName())) {
        result.add(snapshot);
      }
    }
    return result;
  }

  /**
   * Initial state: two independent annotated sources; the processor generates a companion class for both
   * and registers both in the META-INF/MockList and META-INF/MockHints resources.
   * Change: BenchFirst's mode switches from Throughput to AverageTime and the annotated method
   * return type changes from int to long, so the processor regenerates different outputs for this source.
   * The change makes the whole resource origin group recompile (asserted by the golden build.log).
   * Expected: outputs generated for the unchanged BenchSecond stay byte-identical, outputs for the changed
   * BenchFirst are regenerated, both aggregate resources contain complete data for both sources.
   */
  @Test
  public void testMockAptOutputRegeneration() throws Exception {
    performTest("java/apt/mockOutputRegeneration").assertSuccessful();

    List<JarSnapshot> snapshots = snapshots("a.jar");
    assertEquals("Expected exactly two jar snapshots: after the initial and after the incremental build", 2, snapshots.size());
    JarSnapshot initial = snapshots.get(0);
    JarSnapshot incremental = snapshots.get(1);

    String changedGen = "a/gen/BenchFirstGen.class";
    String unchangedGen = "a/gen/BenchSecondGen.class";

    assertTrue("The processor must have generated companion classes in the initial build: " + initial.entryDigests().keySet(),
               initial.entryDigests().containsKey(changedGen) && initial.entryDigests().containsKey(unchangedGen));
    String initialList = initial.metaInfContent().get(MOCK_LIST);
    assertNotNull("The processor must have generated " + MOCK_LIST + " in the initial build", initialList);
    assertTrue("Both classes must be registered in " + MOCK_LIST + " after the initial build: " + initialList,
               initialList.contains("a.BenchFirst#first=Throughput") && initialList.contains("a.BenchSecond#second=Throughput"));

    // the change keeps the set of generated outputs stable, so both builds must produce the same entry set
    assertEquals("The set of jar entries must not change after the incremental build",
                 initial.entryDigests().keySet(), incremental.entryDigests().keySet());

    // outputs of the unchanged source must stay unchanged
    assertEquals("Output of the unchanged source must stay unchanged: a/BenchSecond.class",
                 initial.entryDigests().get("a/BenchSecond.class"), incremental.entryDigests().get("a/BenchSecond.class"));
    assertEquals("Generated output of the unchanged source must stay unchanged: " + unchangedGen,
                 initial.entryDigests().get(unchangedGen), incremental.entryDigests().get(unchangedGen));

    // the changed return type of the annotated method produces different code in the generated class
    assertNotEquals("Generated output of the changed source must be regenerated: " + changedGen,
                    initial.entryDigests().get(changedGen), incremental.entryDigests().get(changedGen));

    String list = incremental.metaInfContent().get(MOCK_LIST);
    assertNotNull(MOCK_LIST + " must be present in the output after the incremental build", list);
    assertTrue("Regenerated " + MOCK_LIST + " must reflect the changed mode of the changed source: " + list,
               list.contains("a.BenchFirst#first=AverageTime"));
    assertTrue("Entry of the unchanged source must be preserved in " + MOCK_LIST + ": " + list,
               list.contains("a.BenchSecond#second=Throughput"));

    String hints = incremental.metaInfContent().get(MOCK_HINTS);
    assertNotNull(MOCK_HINTS + " must be present in the output after the incremental build", hints);
    assertTrue("Hint of the changed source must be present in " + MOCK_HINTS + ": " + hints,
               hints.contains("inline,a.BenchFirst#first"));
    assertTrue("Hint of the unchanged source must be preserved in " + MOCK_HINTS + ": " + hints,
               hints.contains("inline,a.BenchSecond#second"));
  }

  /**
   * Change: one of the two contributing sources is deleted.
   * Expected: the remaining origin group is recompiled, the resources are regenerated from scratch and
   * contain no stale entries of the deleted source; all outputs of the deleted source (the compiled class
   * and the generated companion class) disappear from the jar.
   */
  @Test
  public void testMockAptSourceDeleted() throws Exception {
    performTest("java/apt/mockSourceDeleted").assertSuccessful();

    List<JarSnapshot> snapshots = snapshots("a.jar");
    assertEquals(2, snapshots.size());
    JarSnapshot incremental = snapshots.get(1);

    assertFalse("Output of the deleted source must disappear from the jar",
                incremental.entryDigests().containsKey("a/BenchSecond.class"));
    assertFalse("Generated output of the deleted source must disappear from the jar",
                incremental.entryDigests().containsKey("a/gen/BenchSecondGen.class"));

    String list = incremental.metaInfContent().get(MOCK_LIST);
    assertNotNull(MOCK_LIST + " must be present in the output after the incremental build", list);
    assertTrue("Entry of the remaining source must be present in " + MOCK_LIST + ": " + list, list.contains("a.BenchFirst#first=Throughput"));
    assertFalse("No stale entries of the deleted source are allowed in " + MOCK_LIST + ": " + list, list.contains("BenchSecond"));

    String hints = incremental.metaInfContent().get(MOCK_HINTS);
    assertNotNull(MOCK_HINTS + " must be present in the output after the incremental build", hints);
    assertTrue("Hint of the remaining source must be present in " + MOCK_HINTS + ": " + hints, hints.contains("inline,a.BenchFirst#first"));
    assertFalse("No stale entries of the deleted source are allowed in " + MOCK_HINTS + ": " + hints, hints.contains("BenchSecond"));
  }

  /**
   * Change: a new annotated source is added to the target.
   * Expected: only the new source is compiled (existing origins are not affected by an addition);
   * the merge-capable MockList resource accumulates the new entry while preserving the entries
   * of the not-recompiled sources (served to the processor from the previous build's output).
   * Note: the write-only MockHints resource is regenerated from the current session only, so on additions
   * it retains just the new source's hints — the known cost of a processor that never merges its output;
   * the assertions below deliberately do not require preserved hints for the unchanged sources.
   */
  @Test
  public void testMockAptSourceAdded() throws Exception {
    performTest("java/apt/mockSourceAdded").assertSuccessful();

    List<JarSnapshot> snapshots = snapshots("a.jar");
    assertEquals(2, snapshots.size());
    JarSnapshot initial = snapshots.get(0);
    JarSnapshot incremental = snapshots.get(1);

    assertTrue("Output of the added source must appear in the jar",
               incremental.entryDigests().containsKey("a/BenchThird.class"));
    assertTrue("Generated output of the added source must appear in the jar",
               incremental.entryDigests().containsKey("a/gen/BenchThirdGen.class"));

    // existing sources are not recompiled by an addition
    assertEquals("Output of the unchanged source must stay unchanged: a/BenchFirst.class",
                 initial.entryDigests().get("a/BenchFirst.class"), incremental.entryDigests().get("a/BenchFirst.class"));
    assertEquals("Generated output of the unchanged source must stay unchanged: a/gen/BenchFirstGen.class",
                 initial.entryDigests().get("a/gen/BenchFirstGen.class"), incremental.entryDigests().get("a/gen/BenchFirstGen.class"));

    String list = incremental.metaInfContent().get(MOCK_LIST);
    assertNotNull(MOCK_LIST + " must be present in the output after the incremental build", list);
    assertTrue("Entry of the added source must be registered in " + MOCK_LIST + ": " + list,
               list.contains("a.BenchThird#third=Throughput"));
    assertTrue("Entries of the unchanged sources must be preserved in " + MOCK_LIST + ": " + list,
               list.contains("a.BenchFirst#first=Throughput") && list.contains("a.BenchSecond#second=Throughput"));

    String hints = incremental.metaInfContent().get(MOCK_HINTS);
    assertNotNull(MOCK_HINTS + " must be present in the output after the incremental build", hints);
    assertTrue("Hint of the added source must be present in " + MOCK_HINTS + ": " + hints, hints.contains("inline,a.BenchThird#third"));
  }

  /**
   * Change: one of the two contributing sources loses its annotation (the class itself remains).
   * Expected: the origin group is recompiled; the no-longer-contributing class keeps its compiled output,
   * but its generated companion class disappears from the jar and its entries disappear from both resources.
   */
  @Test
  public void testMockAptAnnotationRemoved() throws Exception {
    performTest("java/apt/mockAnnotationRemoved").assertSuccessful();

    List<JarSnapshot> snapshots = snapshots("a.jar");
    assertEquals(2, snapshots.size());
    JarSnapshot incremental = snapshots.get(1);

    assertTrue("The no-longer-annotated class must still be compiled",
               incremental.entryDigests().containsKey("a/BenchSecond.class"));
    assertFalse("Generated output of the no-longer-annotated class must disappear from the jar",
                incremental.entryDigests().containsKey("a/gen/BenchSecondGen.class"));

    String list = incremental.metaInfContent().get(MOCK_LIST);
    assertNotNull(MOCK_LIST + " must be present in the output after the incremental build", list);
    assertTrue("Entry of the still-annotated source must be present in " + MOCK_LIST + ": " + list, list.contains("a.BenchFirst#first=Throughput"));
    assertFalse("No stale entries of the no-longer-annotated source are allowed in " + MOCK_LIST + ": " + list, list.contains("BenchSecond"));

    String hints = incremental.metaInfContent().get(MOCK_HINTS);
    assertNotNull(MOCK_HINTS + " must be present in the output after the incremental build", hints);
    assertTrue("Hint of the still-annotated source must be present in " + MOCK_HINTS + ": " + hints, hints.contains("inline,a.BenchFirst#first"));
    assertFalse("No stale entries of the no-longer-annotated source are allowed in " + MOCK_HINTS + ": " + hints, hints.contains("BenchSecond"));
  }

  /**
   * Two annotation processors with disjoint source groups: MockBenchProcessor aggregates BenchOne and BenchTwo
   * into MockList/MockHints, MockMarkProcessor aggregates Marked into MarkList. The resources are attributed
   * per processor, so changing BenchOne recompiles only the Bench group (asserted by the golden build.log):
   * Marked.java is not recompiled and MarkList is preserved byte-identical.
   */
  @Test
  public void testMockAptTwoProcessors() throws Exception {
    performTest("java/apt/mockTwoProcessors").assertSuccessful();

    List<JarSnapshot> snapshots = snapshots("a.jar");
    assertEquals(2, snapshots.size());
    JarSnapshot initial = snapshots.get(0);
    JarSnapshot incremental = snapshots.get(1);

    // the other processor's source group is not affected
    assertEquals("Output of the other processor's source must stay unchanged: a/Marked.class",
                 initial.entryDigests().get("a/Marked.class"), incremental.entryDigests().get("a/Marked.class"));
    assertEquals("Generated output of the other processor's source must stay unchanged: a/gen/MarkedMarkGen.class",
                 initial.entryDigests().get("a/gen/MarkedMarkGen.class"), incremental.entryDigests().get("a/gen/MarkedMarkGen.class"));
    assertEquals("The other processor's resource must stay byte-identical: " + MARK_LIST,
                 initial.entryDigests().get(MARK_LIST), incremental.entryDigests().get(MARK_LIST));

    // the changed processor's group is fully regenerated
    String list = incremental.metaInfContent().get(MOCK_LIST);
    assertNotNull(MOCK_LIST + " must be present in the output after the incremental build", list);
    assertTrue("Regenerated " + MOCK_LIST + " must reflect the changed mode: " + list, list.contains("a.BenchOne#one=AverageTime"));
    assertTrue("Entry of the unchanged group member must be preserved in " + MOCK_LIST + ": " + list, list.contains("a.BenchTwo#two=Throughput"));

    String markList = incremental.metaInfContent().get(MARK_LIST);
    assertNotNull(MARK_LIST + " must be present in the output after the incremental build", markList);
    assertTrue("The other processor's resource content must be intact: " + markList, markList.contains("a.Marked#mark"));
  }

  private static String sha256(byte[] content) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    }
    catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  }
}
