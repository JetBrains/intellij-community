// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.versioning

import com.intellij.openapi.Disposable
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.Disposer
import com.intellij.platform.testFramework.junit5.codeInsight.psi.LightweightCommitScenario
import com.intellij.platform.testFramework.junit5.codeInsight.psi.assertLightweightCommitScenario
import com.intellij.platform.testFramework.junit5.codeInsight.psi.replaceBetween
import com.intellij.platform.testFramework.junit5.codeInsight.psi.runVersionedTest
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.augment.PsiAugmentProvider
import com.intellij.psi.javadoc.PsiDocComment
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.editorFixture
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.psiFileFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

/**
 * AI-generated test.
 *
 * Checks the behavior of lightweight commit across various Java language structures.
 */
@TestApplication
internal class JavaLightweightDocumentCommitTest {

  private companion object {
    private const val TEXT_BLOCK_QUOTES_PLACEHOLDER = "__TEXT_BLOCK_QUOTES__"

    @Language("JAVA")
    private val BASE_TEXT: String = """
      package versioning.stress;
      
      import java.io.Closeable;
      import java.io.IOException;
      import java.lang.annotation.ElementType;
      import java.lang.annotation.Retention;
      import java.lang.annotation.RetentionPolicy;
      import java.lang.annotation.Target;
      import java.time.Instant;
      import java.util.ArrayList;
      import java.util.Collection;
      import java.util.Iterator;
      import java.util.LinkedHashMap;
      import java.util.List;
      import java.util.Locale;
      import java.util.Map;
      import java.util.Objects;
      import java.util.Optional;
      import java.util.concurrent.Callable;
      import java.util.function.Function;
      
      import static java.util.stream.Collectors.joining;
      
      /**
       * Stress fixture for lightweight document commit.
       * <p>
       * The source intentionally mixes Java language constructs which produce different Java PSI nodes:
       * declarations, expressions, comments, javadocs, text blocks, nested types, lambdas, and switches.
       * </p>
       * {@snippet :
       *   var fixture = new FeatureStress<Integer>(1);
       *   fixture.describe("snippet");
       * }
       *
       * @param <T> numeric value type used by the iterable contract
       * @see FeatureStress#describe(Object)
       */
      public final class FeatureStress<T extends Number & Comparable<T>> implements Iterable<T> {
        /** Type-use annotation used in fields, parameters, and record components. */
        @Target({ElementType.TYPE_USE, ElementType.PARAMETER, ElementType.METHOD, ElementType.FIELD, ElementType.RECORD_COMPONENT})
        @Retention(RetentionPolicy.RUNTIME)
        @interface Marker {
          String value();
          int[] numbers() default {};
        }
      
        private static final String TEMPLATE = TEXT_BLOCK_QUOTES_PLACEHOLDER
            line one
              line two with "quotes"
            line three
            TEXT_BLOCK_QUOTES_PLACEHOLDER;
      
        private static final Map<String, List<? extends Number>> DEFAULTS = Map.of(
          "integers", List.of(1, 2, 3),
          "mixed", List.of(1, 2L, 3.0)
        );
      
        private final Object lock = new Object();
        private final T value;
        private final List<@Marker(value = "name", numbers = {1, 2, 3}) String> names = new ArrayList<>();
      
        /**
         * Creates a fixture value.
         *
         * @param value the numeric value to expose through {@link #iterator()}
         */
        public FeatureStress(@Marker("value") T value) {
          this.value = Objects.requireNonNull(value, "value");
          names.add(TEMPLATE.stripIndent());
        }
      
        // MEMBERS_START
      
        /**
         * Describes an arbitrary input with a Java 21 switch expression.
         *
         * @param input anything accepted by the fixture
         * @return a stable textual representation
         */
        public String describe(Object input) {
          // DESCRIBE_BODY_START
          return switch (input) {
            case null -> "null";
            case String text when !text.isBlank() -> text.strip();
            case Integer integer -> "integer:" + integer;
            case Shape shape -> shape.name();
            case List<?> list when !list.isEmpty() -> summarize(list);
            default -> Objects.toString(input);
          };
          // DESCRIBE_BODY_END
        }
      
        /** Combines streams, method references, anonymous classes, and a local class. */
        public String compute(List<? extends T> values, Function<? super T, ? extends String> renderer) throws Exception {
          Runnable callback = new Runnable() {
            @Override
            public void run() {
              names.add("anonymous");
            }
          };
          callback.run();
      
          class LocalCallable implements Callable<String> {
            @Override
            public String call() {
              return values.stream().map(renderer).collect(joining(","));
            }
          }
      
          return new LocalCallable().call();
        }
      
        @SafeVarargs
        public final List<T> copyAll(T first, T... more) {
          List<T> copy = new ArrayList<>();
          copy.add(first);
          for (T item : more) {
            copy.add(item);
          }
          return copy;
        }
      
        public String risky(Mode mode) throws IOException {
          try (Resource resource = new Resource(mode.label())) {
            return resource.read();
          }
          catch (IOException | IllegalStateException ex) {
            throw ex;
          }
          finally {
            synchronized (lock) {
              names.add("closed:" + Instant.EPOCH);
            }
          }
        }
      
        public String tokenText(Token token) {
          return switch (token) {
            case WordToken word -> word.text();
            case NumberToken number -> number.text();
          };
        }
      
        private static String summarize(Collection<?> values) {
          return values.stream().map(Objects::toString).collect(joining("|"));
        }
      
        @Override
        public Iterator<T> iterator() {
          return List.of(value).iterator();
        }
      
        public sealed interface Shape permits Circle, Rectangle, Group {
          String name();
        }
      
        public record Circle(@Marker("radius") double radius) implements Shape {
          @Override
          public String name() {
            return "circle:" + radius;
          }
        }
      
        public record Rectangle(double width, double height) implements Shape {
          @Override
          public String name() {
            return "rectangle:" + width + "x" + height;
          }
        }
      
        public static final class Group implements Shape {
          private final List<Shape> shapes;
      
          public Group(List<Shape> shapes) {
            this.shapes = List.copyOf(shapes);
          }
      
          @Override
          public String name() {
            return shapes.stream().map(Shape::name).collect(joining("+"));
          }
        }
      
        public sealed static abstract class Token permits WordToken, NumberToken {
          abstract String text();
        }
      
        public static final class WordToken extends Token {
          private final String text;
      
          public WordToken(String text) {
            this.text = text;
          }
      
          @Override
          String text() {
            return text;
          }
        }
      
        public static non-sealed class NumberToken extends Token {
          private final Number number;
      
          public NumberToken(Number number) {
            this.number = number;
          }
      
          @Override
          String text() {
            return String.valueOf(number);
          }
        }
      
        enum Mode {
          FAST("fast"),
          // LEGACY_CONSTANTS_START
          SAFE("safe"),
          LEGACY("legacy");
          // LEGACY_CONSTANTS_END
      
          private final String label;
      
          Mode(String label) {
            this.label = label;
          }
      
          String label() {
            return label.toUpperCase(Locale.ROOT);
          }
        }
      
        // LEGACY_ADAPTER_START
        static final class LegacyAdapter {
          Map<String, List<String>> adapt(String key, String... values) {
            Map<String, List<String>> result = new LinkedHashMap<>();
            result.put(key, List.of(values));
            return result;
          }
        }
        // LEGACY_ADAPTER_END
      
        static final class Resource implements Closeable {
          private final String name;
      
          Resource(String name) {
            this.name = name;
          }
          String read() {
            return name + DEFAULTS.keySet();
          }
          @Override
          public void close() throws IOException {
            if (name.isEmpty()) {
              throw new IOException("empty");
            }
          }
        }
      
        // MEMBERS_END
      }
    """.trimIndent().replace(TEXT_BLOCK_QUOTES_PLACEHOLDER, "\"\"\"")

    @Language("JAVA")
    private val REPLACED_DESCRIBE_BODY = """
          String computed = switch (input) {
            case null -> "replacement:null";
            case Mode mode -> "mode:" + mode.label();
            case Token token -> tokenText(token);
            case Shape shape when shape.name().contains(":") -> "shape:" + shape.name();
            default -> Objects.toString(input, "fallback");
          };
          return computed;
    """.trimIndent().prependIndent("    ") + "\n"

    @Language("JAVA")
    private val INSERTED_MEMBER = """
        /**
         * Added by the lightweight commit test to cover generic methods and nested collection types.
         *
         * @param sink accepts the same value through a lower bound wildcard
         * @param value value to store and return
         * @return optional value visible only in the committed PSI branch
         */
        @Marker("inserted")
        public <R extends CharSequence> Optional<R> insertedFeature(List<? super R> sink, R value) {
          sink.add(value);
          return Optional.of(value);
        }
      
    """.trimIndent().prependIndent("  ")

    private fun replaceDescribeBody(): String = BASE_TEXT.replaceBetween(
      "    // DESCRIBE_BODY_START\n",
      "    // DESCRIBE_BODY_END",
      REPLACED_DESCRIBE_BODY,
    )

    private fun insertMember(text: String): String = text.replace(
      "  // MEMBERS_END",
      "${INSERTED_MEMBER}  // MEMBERS_END",
    )

    private fun removeLegacyDeclarations(text: String): String = text
      .replaceBetween(
        "    // LEGACY_CONSTANTS_START\n",
        "    // LEGACY_CONSTANTS_END",
        "    SAFE(\"safe\");\n",
      )
      .replaceBetween(
        "  // LEGACY_ADAPTER_START\n",
        "  // LEGACY_ADAPTER_END",
        "",
      )

    /**
     * Adapts the class-level assertions below to the file-level assertions expected by the shared harness:
     * every scenario asserts against the single top-level `FeatureStress` class.
     */
    private fun javaScenario(
      name: String,
      updatedText: String,
      assertScenarioPsi: (PsiClass) -> Unit,
    ): LightweightCommitScenario<PsiJavaFile> = LightweightCommitScenario(name, updatedText) { javaFile ->
      assertScenarioPsi(javaFile.classes.single { it.name == "FeatureStress" })
    }

    private val lightweightCommitScenarios = listOf(
      javaScenario(
        name ="replace complex switch method body",
        updatedText = replaceDescribeBody(),
        assertScenarioPsi = { mainClass ->
          val describe = mainClass.findMethodsByName("describe", false).single()
          Assertions.assertTrue(describe.text.contains("case Mode mode"), describe.text)
          Assertions.assertTrue(describe.text.contains("return computed;"), describe.text)
        },
      ),
      javaScenario(
        name ="insert javadoc annotation and generic member",
        updatedText = insertMember(BASE_TEXT),
        assertScenarioPsi = { mainClass ->
          val inserted = mainClass.findMethodsByName("insertedFeature", false).singleOrNull()
          Assertions.assertNotNull(inserted, "Inserted generic method should be available in lightweight committed PSI")
          Assertions.assertTrue(inserted!!.docComment!!.text.contains("lower bound wildcard"))
          Assertions.assertEquals("R", inserted.typeParameters.single().name)
        },
      ),
      javaScenario(
        name ="remove enum constant and nested declaration",
        updatedText = removeLegacyDeclarations(BASE_TEXT),
        assertScenarioPsi = { mainClass ->
          val mode = mainClass.findInnerClassByName("Mode", false)!!
          Assertions.assertFalse(mode.fields.any { it.name == "LEGACY" }, mode.text)
          Assertions.assertNull(mainClass.findInnerClassByName("LegacyAdapter", false))
        },
      ),
      javaScenario(
        name ="wide update with replacements insertions and removals",
        updatedText = removeLegacyDeclarations(insertMember(replaceDescribeBody())),
        assertScenarioPsi = { mainClass ->
          Assertions.assertNotNull(mainClass.findMethodsByName("insertedFeature", false).singleOrNull())
          Assertions.assertTrue(mainClass.findMethodsByName("describe", false).single().text.contains("case Token token"))
          Assertions.assertNull(mainClass.findInnerClassByName("LegacyAdapter", false))
        },
      ),
    )

    private fun assertJavaShape(javaFile: PsiJavaFile) {
      val mainClass = javaFile.classes.single { it.name == "FeatureStress" }
      Assertions.assertNotNull(mainClass.findFieldByName("names", false))
      Assertions.assertNotNull(mainClass.findMethodsByName("describe", false).singleOrNull())
      Assertions.assertNotNull(mainClass.findInnerClassByName("Circle", false))
      Assertions.assertNotNull(mainClass.findInnerClassByName("Mode", false))
      Assertions.assertTrue(PsiTreeUtil.findChildrenOfType(javaFile, PsiDocComment::class.java).size >= 5)
    }
  }

  private val _project = projectFixture(openAfterCreation = true)
  private val _module = _project.moduleFixture("src")
  private val _sourceRoot = _module.sourceRootFixture()
  private val _psiFile = _sourceRoot.psiFileFixture("FeatureStress.java", "package versioning.stress; final class FeatureStress {}")
  private val _editor = _psiFile.editorFixture()

  private val project by _project
  private val module: Module by _module
  private val editor by _editor

  private lateinit var maskDisposable: Disposable

  @BeforeEach
  fun awaitIndexing() {
    IndexingTestUtil.waitUntilIndexesAreReady(project)
  }

  @BeforeEach
  fun removeComplexAugmenters() {
    maskDisposable = Disposer.newDisposable()
    // in CI, we have lombok in classpath, which does some dark stuff in PsiAugmentProvider during search of methods in class.
    // PsiAugmentProvider is not needed for lightweight commit though, so it is safe to ignore it.
    ExtensionTestUtil.maskExtensions(PsiAugmentProvider.EP_NAME, listOf(), maskDisposable)
  }

  @AfterEach
  fun restoreComplexAugmenters() {
    Disposer.dispose(maskDisposable)
  }

  /**
   * AI-generated test.
   *
   * Tests lightweight commits for a method replacement, a member insertion, declaration removals, and one combined Java edit.
   */
  @TestFactory
  fun `lightweight commit handles broad java syntax edits`(): List<DynamicTest> = lightweightCommitScenarios.map { scenario ->
    DynamicTest.dynamicTest(scenario.name) {
      IdeaTestUtil.withLevel(module, LanguageLevel.JDK_21) {
        runVersionedTest(project) {
          assertLightweightCommitScenario(project, editor.document, BASE_TEXT, scenario, ::assertJavaShape)
        }
      }
    }
  }
}
