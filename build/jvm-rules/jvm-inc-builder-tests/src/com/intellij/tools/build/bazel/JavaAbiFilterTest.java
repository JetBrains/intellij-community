package com.intellij.tools.build.bazel;

import com.intellij.tools.build.bazel.impl.BazelIncBuildTest;
import com.intellij.tools.build.bazel.jvmIncBuilder.DataPaths;
import com.intellij.tools.build.bazel.jvmIncBuilder.instrumentation.FailSafeClassReader;
import org.jetbrains.jps.dependency.diff.DiffCapable;
import org.jetbrains.jps.dependency.diff.Difference;
import org.jetbrains.jps.dependency.java.JvmClass;
import org.jetbrains.jps.dependency.java.JvmClassNodeBuilder;
import org.jetbrains.jps.dependency.java.Proto;
import org.jetbrains.org.objectweb.asm.Opcodes;
import org.junit.Test;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.jetbrains.jps.util.Iterators.count;
import static org.jetbrains.jps.util.Iterators.find;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;


public class JavaAbiFilterTest extends BazelIncBuildTest {
  @Test
  public void testJavaAbiFiltering() throws Exception {
    performTest(0, "worker/javaAbiFiltering").assertSuccessful();
  }

  @Override
  protected void validateOutputArtifacts(BuildOutput output) throws IOException {
    super.validateOutputArtifacts(output);

    Path outJar = output.outputJar();
    Path abiJar = outJar.resolveSibling(DataPaths.truncateExtension(getFileName(outJar)) + DataPaths.ABI_JAR_SUFFIX);
    
    if (!Files.exists(abiJar)) {
      fail("ABI output artifact not found: " + abiJar);
    }

    Set<JvmClass> outputNodes = loadLodes(outJar);
    Set<JvmClass> abiNodes = loadLodes(abiJar);
    Difference.Specifier<JvmClass, JvmClass.Diff> diff = Difference.deepDiff(outputNodes, abiNodes);
    assertEquals("The classes in ABI jar for this test are supposed to be either removed completely or changed compared to the original output", count(outputNodes), count(diff.changed()) + count(diff.removed()));
    assertABIFiltering("CLASSES", outputNodes, abiNodes);

    for (Difference.Change<JvmClass, JvmClass.Diff> change : diff.changed()) {
      JvmClass node = change.getPast();
      JvmClass abiNode = change.getNow();
      assertABIFiltering("FIELDS", node.getFields(), abiNode.getFields());
      assertABIFiltering("METHODS", node.getMethods(), abiNode.getMethods());
    }
  }

  /**
   * Reflects settings in JavaAbiClassFilter.isAbiVisible(access)
   * Adjust test expectations if this logic changes
   * @param access flags on an element
   */
  private static boolean expectedAbiVisible(int access) {
    // include into ABI: public, protected, package-local elements
    return (access & Opcodes.ACC_PRIVATE) == 0;
  }

  private static <T extends Proto & DiffCapable<T, ?>> void assertABIFiltering(String memberKind, Iterable<T> elements, Iterable<T> abiElements) {
    int retainedCount = 0;
    for (T elem : elements) {
      int access = elem.getFlags().getValue();
      if (expectedAbiVisible(access)) {
        assertNotNull(memberKind + " " + elem.getName() + " should be retained, but it was stripped", find(abiElements, elem::isSame));
        retainedCount += 1;
      }
      else {
        assertNull(memberKind + " " + elem.getName() + " should be stripped, but it was retained", find(abiElements, elem::isSame));
      }
    }
    assertEquals(retainedCount, count(abiElements));
  }

  private static Set<JvmClass> loadLodes(Path jar) throws IOException {
    Set<JvmClass> result = new HashSet<>();
    try (var zis = new ZipInputStream(new BufferedInputStream(Files.newInputStream(jar)))) {
      for (ZipEntry entry = zis.getNextEntry(); entry != null; entry = zis.getNextEntry()) {
        String path = entry.getName();
        if (path.endsWith(".class")) {
          var node = JvmClassNodeBuilder.create(path, new FailSafeClassReader(zis.readAllBytes()), false).getResult();

          assertTrue("JvmClass nodes only are expected in the output jar for this test. Got " + node.getClass().getName() + " instead", node instanceof JvmClass);

          result.add((JvmClass)node);
        }
      }
    }
    return result;
  }
}
