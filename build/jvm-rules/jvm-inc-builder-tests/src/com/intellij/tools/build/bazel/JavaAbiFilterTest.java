package com.intellij.tools.build.bazel;

import com.intellij.tools.build.bazel.impl.BazelIncBuildTest;
import com.intellij.tools.build.bazel.jvmIncBuilder.DataPaths;
import com.intellij.tools.build.bazel.jvmIncBuilder.impl.JavaAbiClassFilter;
import com.intellij.tools.build.bazel.jvmIncBuilder.impl.JavaAbiFilter;
import com.intellij.tools.build.bazel.jvmIncBuilder.instrumentation.FailSafeClassReader;
import org.jetbrains.jps.dependency.diff.DiffCapable;
import org.jetbrains.jps.dependency.diff.Difference;
import org.jetbrains.jps.dependency.java.JvmClass;
import org.jetbrains.jps.dependency.java.JvmClassNodeBuilder;
import org.jetbrains.jps.dependency.java.Proto;
import org.jetbrains.org.objectweb.asm.ClassReader;
import org.jetbrains.org.objectweb.asm.ClassVisitor;
import org.jetbrains.org.objectweb.asm.Opcodes;
import org.junit.Test;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

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

    Map<String, byte[]> outputClasses = readClasses(outJar);
    Map<String, byte[]> abiOutputClasses = readClasses(abiJar);
    Set<JvmClass> outputNodes = toNodes(outputClasses);

    // the abi jar the worker produced follows the mode the worker ships with
    validateAbiFiltering(JavaAbiFilter.FILTERING_MODE, outputNodes, toNodes(abiOutputClasses));

    // both filtering modes, applied in-process to the same compiled classes
    for (JavaAbiClassFilter.Mode mode : JavaAbiClassFilter.Mode.values()) {
      Map<String, byte[]> abiClasses;
      if (mode == JavaAbiFilter.FILTERING_MODE) {
        abiClasses = abiOutputClasses;
      }
      else {
        abiClasses = new HashMap<>();
        for (Map.Entry<String, byte[]> entry : outputClasses.entrySet()) {
          byte[] content = JavaAbiClassFilter.filter(mode, entry.getValue());
          if (content != null) {
            abiClasses.put(entry.getKey(), content);
          }
        }
        validateAbiFiltering(mode, outputNodes, toNodes(abiClasses));
      }
      assertNestMembersPruned(outputClasses, abiClasses);
      assertPrivateClassesAreRequired(mode, abiClasses);
    }
  }

  /**
   * The consumer uses only non-private surfaces of PublicClass, but javac must complete the
   * private classes behind them: the supertype of Bridge, the private encloser of the inherited
   * member type Q.PublicNested, and the return type of leak().
   */
  private static final String CONSUMER_SOURCE = """
    package ppp;

    public class Consumer {
      Object bridge() { return new PublicClass.Bridge(); }
      PublicClass.Q.PublicNested inherited() { return null; }
      Object leaked() { return PublicClass.leak(); }
    }
    """;

  private static final List<String> PRIVATE_LEAK_TARGETS = List.of(
    "ppp/PublicClass$PrivateBase.class", "ppp/PublicClass$PrivateHolder.class", "ppp/PublicClass$PrivateReturn.class");

  /**
   * Shows WHY named private classes stay in the ABI. A consumer that uses only non-private
   * surfaces still makes javac complete the private classes behind them. The consumer compiles
   * against the full ABI, and does not compile when the private class files are removed.
   */
  private static void assertPrivateClassesAreRequired(JavaAbiClassFilter.Mode mode, Map<String, byte[]> abiClasses) throws IOException {
    assertTrue("[" + mode + "] the private class files must be in the ABI", abiClasses.keySet().containsAll(PRIVATE_LEAK_TARGETS));

    String fullAbiErrors = compileConsumer(abiClasses);
    assertNull("[" + mode + "] the consumer must compile against the full ABI, but got:\n" + fullAbiErrors, fullAbiErrors);

    Map<String, byte[]> withoutPrivate = new HashMap<>(abiClasses);
    for (String target : PRIVATE_LEAK_TARGETS) {
      withoutPrivate.remove(target);
    }
    String errors = compileConsumer(withoutPrivate);
    assertNotNull("[" + mode + "] the consumer must not compile when the ABI drops the private class files", errors);
    assertTrue("[" + mode + "] the compilation errors must name a private class:\n" + errors, errors.contains("Private"));
  }

  /** Returns null when the source compiles against the given classes, or the error text otherwise. */
  private static String compileConsumer(Map<String, byte[]> classpathClasses) throws IOException {
    Path dir = Files.createTempDirectory("abi-consumer");
    Path classpathJar = dir.resolve("abi.jar");
    try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(classpathJar))) {
      for (Map.Entry<String, byte[]> entry : classpathClasses.entrySet()) {
        zos.putNextEntry(new ZipEntry(entry.getKey()));
        zos.write(entry.getValue());
        zos.closeEntry();
      }
    }

    JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
    assertNotNull("the test needs a JDK with the system java compiler", javac);
    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
    try (StandardJavaFileManager fileManager = javac.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
      fileManager.setLocation(StandardLocation.CLASS_PATH, List.of(classpathJar.toFile()));
      fileManager.setLocation(StandardLocation.CLASS_OUTPUT, List.of(dir.toFile()));
      JavaFileObject source = new SimpleJavaFileObject(URI.create("string:///ppp/Consumer.java"), JavaFileObject.Kind.SOURCE) {
        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
          return CONSUMER_SOURCE;
        }
      };
      try {
        if (javac.getTask(null, fileManager, diagnostics, null, null, List.of(source)).call()) {
          return null;
        }
      }
      catch (RuntimeException e) {
        return String.valueOf(e); // javac surfaces some completion failures as a crash instead of a diagnostic
      }
      return String.valueOf(diagnostics.getDiagnostics());
    }
  }

  private static void validateAbiFiltering(JavaAbiClassFilter.Mode mode, Set<JvmClass> outputNodes, Set<JvmClass> abiNodes) {
    Difference.Specifier<JvmClass, JvmClass.Diff> diff = Difference.deepDiff(outputNodes, abiNodes);
    assertEquals("[" + mode + "] The classes in ABI jar for this test are supposed to be either removed completely or changed compared to the original output", count(outputNodes), count(diff.changed()) + count(diff.removed()));
    assertClassFiltering(mode, outputNodes, abiNodes);

    for (Difference.Change<JvmClass, JvmClass.Diff> change : diff.changed()) {
      JvmClass node = change.getPast();
      JvmClass abiNode = change.getNow();
      // in VERIFIABLE_BYTECODE mode a visible enum keeps every method (real or stubbed) and its
      // synthetic fields, so the loaded enum class stays functional - see EnumMethodContainer
      boolean enumKeptWhole = mode == JavaAbiClassFilter.Mode.VERIFIABLE_BYTECODE && isEnum(node.getFlags().getValue());
      assertABIFiltering("FIELDS", node.getFields(), abiNode.getFields(),
                         enumKeptWhole? (access, _) -> expectedAbiVisible(access) || isSynthetic(access) : (access, _) -> expectedAbiVisible(access));
      BiPredicate<Integer, String> methodRetained = mode == JavaAbiClassFilter.Mode.IJAR_COMPLIANT? JavaAbiFilterTest::expectedMethodRetained
        : enumKeptWhole? (_, _) -> true : (access, _) -> expectedAbiVisible(access);
      assertABIFiltering("METHODS", node.getMethods(), abiNode.getMethods(), methodRetained);
    }
  }

  private static boolean isEnum(int access) {
    return (access & Opcodes.ACC_ENUM) != 0;
  }

  private static boolean isSynthetic(int access) {
    return (access & Opcodes.ACC_SYNTHETIC) != 0;
  }

  /**
   * Reflects the IJAR_COMPLIANT method drop rules: private methods, synthetic non-bridge
   * methods, and the static initializer are dropped. VERIFIABLE_BYTECODE keeps the visibility
   * rule only.
   */
  private static boolean expectedMethodRetained(int access, String name) {
    if ("<clinit>".equals(name) || isSynthetic(access) && (access & Opcodes.ACC_BRIDGE) == 0) {
      return false;
    }
    return expectedAbiVisible(access);
  }

  /**
   * A NestMembers name must denote a class file that stays in the ABI:
   * both modes prune the members whose class files the filter drops.
   */
  private static void assertNestMembersPruned(Map<String, byte[]> classes, Map<String, byte[]> abiClasses) {
    for (Map.Entry<String, byte[]> entry : abiClasses.entrySet()) {
      Set<String> expected = readNestMembers(classes.get(entry.getKey()));
      expected.removeIf(member -> !abiClasses.containsKey(member + ".class"));
      assertEquals("NestMembers of " + entry.getKey() + " must list exactly the members whose class files stay in the ABI", expected, readNestMembers(entry.getValue()));
    }
  }

  private static Set<String> readNestMembers(byte[] classBytes) {
    Set<String> members = new HashSet<>();
    new FailSafeClassReader(classBytes).accept(new ClassVisitor(Opcodes.API_VERSION) {
      @Override
      public void visitNestMember(String nestMember) {
        members.add(nestMember);
      }
    }, ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES | ClassReader.SKIP_DEBUG);
    return members;
  }

  /**
   * Reflects the class file drop rules in JavaAbiClassFilter.filter: both modes drop only local
   * and anonymous classes and every class nested inside them at any depth. A named class stays
   * regardless of its declared visibility.
   * Adjust test expectations if this logic changes
   */
  private static boolean expectedClassRetained(JvmClass cls, Map<String, JvmClass> byName) {
    for (JvmClass c = cls; c != null; c = c.isInnerClass()? byName.get(c.getOuterFqName()) : null) {
      if (c.isLocal() || c.isAnonymous()) {
        return false;
      }
    }
    return true;
  }

  private static void assertClassFiltering(JavaAbiClassFilter.Mode mode, Iterable<JvmClass> classes, Iterable<JvmClass> abiClasses) {
    Map<String, JvmClass> byName = new HashMap<>();
    for (JvmClass cls : classes) {
      byName.put(cls.getName(), cls);
    }
    int retainedCount = 0;
    for (JvmClass cls : classes) {
      if (expectedClassRetained(cls, byName)) {
        assertNotNull("[" + mode + "] CLASSES " + cls.getName() + " should be retained, but it was stripped", find(abiClasses, cls::isSame));
        retainedCount += 1;
      }
      else {
        assertNull("[" + mode + "] CLASSES " + cls.getName() + " should be stripped, but it was retained", find(abiClasses, cls::isSame));
      }
    }
    assertEquals(retainedCount, count(abiClasses));
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

  private static <T extends Proto & DiffCapable<T, ?>> void assertABIFiltering(String memberKind, Iterable<T> elements, Iterable<T> abiElements, BiPredicate<Integer, String> expectedRetained) {
    int retainedCount = 0;
    for (T elem : elements) {
      int access = elem.getFlags().getValue();
      if (expectedRetained.test(access, String.valueOf(elem.getName()))) {
        assertNotNull(memberKind + " " + elem.getName() + " should be retained, but it was stripped", find(abiElements, elem::isSame));
        retainedCount += 1;
      }
      else {
        assertNull(memberKind + " " + elem.getName() + " should be stripped, but it was retained", find(abiElements, elem::isSame));
      }
    }
    assertEquals(retainedCount, count(abiElements));
  }

  private static Map<String, byte[]> readClasses(Path jar) throws IOException {
    Map<String, byte[]> result = new LinkedHashMap<>();
    try (var zis = new ZipInputStream(new BufferedInputStream(Files.newInputStream(jar)))) {
      for (ZipEntry entry = zis.getNextEntry(); entry != null; entry = zis.getNextEntry()) {
        String path = entry.getName();
        if (path.endsWith(".class")) {
          result.put(path, zis.readAllBytes());
        }
      }
    }
    return result;
  }

  private static Set<JvmClass> toNodes(Map<String, byte[]> classes) {
    Set<JvmClass> result = new HashSet<>();
    for (Map.Entry<String, byte[]> entry : classes.entrySet()) {
      var node = JvmClassNodeBuilder.create(entry.getKey(), new FailSafeClassReader(entry.getValue()), false).getResult();

      assertTrue("JvmClass nodes only are expected in the output jar for this test. Got " + node.getClass().getName() + " instead", node instanceof JvmClass);

      result.add((JvmClass)node);
    }
    return result;
  }
}
