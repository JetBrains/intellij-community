// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("JvmAnalysisInspectionFixtures")

package com.intellij.jvm.analysis.internal.testFramework

import com.intellij.codeInspection.DependencyInspection
import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.codeInspection.JavaApiUsageInspection
import com.intellij.codeInspection.MissingDeprecatedAnnotationOnScheduledForRemovalApiInspection
import com.intellij.codeInspection.MustAlreadyBeRemovedApiInspection
import com.intellij.codeInspection.NonExtendableApiInspection
import com.intellij.codeInspection.ObsoleteApiUsageInspection
import com.intellij.codeInspection.OverrideOnlyApiInspection
import com.intellij.codeInspection.SerializableHasSerialVersionUidFieldInspection
import com.intellij.codeInspection.StringToUpperWithoutLocale2Inspection
import com.intellij.codeInspection.SuppressionAnnotationInspection
import com.intellij.codeInspection.SystemGetPropertyInspection
import com.intellij.codeInspection.ThreadRunInspection
import com.intellij.codeInspection.UnstableApiUsageInspection
import com.intellij.codeInspection.ex.InspectionProfileImpl
import com.intellij.jvm.analysis.testFramework.JvmCodeInsightFixture
import com.intellij.jvm.analysis.testFramework.JvmInspectionFixture
import com.intellij.jvm.analysis.testFramework.JvmLanguage
import com.intellij.jvm.analysis.testFramework.jvmCodeInsightFixture
import com.intellij.jvm.analysis.testFramework.jvmInspectionFixture
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.packageDependencies.DependencyRule
import com.intellij.packageDependencies.DependencyValidationManager
import com.intellij.pom.java.LanguageLevel
import com.intellij.profile.codeInspection.InspectionProfileManager
import com.intellij.psi.search.scope.packageSet.NamedScope
import com.intellij.psi.search.scope.packageSet.PackageSetFactory
import com.intellij.refactoring.rename.naming.AutomaticRenamerFactory
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase.ProjectDescriptor
import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.PathUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.io.path.pathString

/**
 * The fixture of a JVM inspection test with a mock JDK of a chosen language level. It is the JUnit 5 form of [JvmSdkInspectionTestBase].
 * Give it the project of [jvmSdkProjectFixture].
 */
@TestOnly
open class JvmSdkInspectionFixture<T : InspectionProfileEntry>(
  base: JvmInspectionFixture<T>,
) : JvmInspectionFixture<T>(base.fixture, base.inspection) {
  /**
   * Sets the language level of the project and of the module to [languageLevel] for this test.
   */
  fun setLanguageLevel(languageLevel: LanguageLevel) {
    runInEdtAndWait {
      IdeaTestUtil.setProjectLanguageLevel(project, languageLevel)
      IdeaTestUtil.setModuleLanguageLevel(module, languageLevel, testRootDisposable)
    }
  }
}

/**
 * Creates the project of a [JvmSdkInspectionFixture] test class, as [JvmSdkInspectionTestBase] does.
 * [sdkLevel] is the language level of the mock JDK. When the mock JDK of this level is not available,
 * [IdeaTestUtil.getMockJdk] picks one.
 */
@TestOnly
@JvmOverloads
fun jvmSdkProjectFixture(sdkLevel: LanguageLevel = LanguageLevel.HIGHEST): TestFixture<LightJavaProject> =
  lightJavaProjectFixture(ProjectDescriptor(sdkLevel))

/**
 * Creates the fixture of a [JavaApiUsageInspection] test. It replaces [JavaApiUsageInspectionTestBase].
 * Give it the project of [jvmSdkProjectFixture].
 */
@TestOnly
@JvmOverloads
fun javaApiUsageInspectionFixture(
  projectFixture: TestFixture<LightJavaProject>,
  testDataPath: String? = null,
): TestFixture<JvmSdkInspectionFixture<JavaApiUsageInspection>> =
  jvmSdkInspectionFixture(projectFixture, testDataPath) { JavaApiUsageInspection() }

/**
 * Creates the fixture of a [ThreadRunInspection] test. It replaces [ThreadRunInspectionTestBase].
 * Give it the project of [jvmSdkProjectFixture].
 */
@TestOnly
@JvmOverloads
fun threadRunInspectionFixture(
  projectFixture: TestFixture<LightJavaProject>,
  testDataPath: String? = null,
): TestFixture<JvmSdkInspectionFixture<ThreadRunInspection>> =
  jvmSdkInspectionFixture(projectFixture, testDataPath) { ThreadRunInspection() }

/**
 * Creates the fixture of a [SerializableHasSerialVersionUidFieldInspection] test. It replaces [SerializableHasSerialVersionUidFieldInspectionTestBase].
 * Give it the project of [jvmSdkProjectFixture].
 */
@TestOnly
@JvmOverloads
fun serializableHasSerialVersionUidFieldInspectionFixture(
  projectFixture: TestFixture<LightJavaProject>,
  testDataPath: String? = null,
): TestFixture<JvmSdkInspectionFixture<SerializableHasSerialVersionUidFieldInspection>> =
  jvmSdkInspectionFixture(projectFixture, testDataPath) { SerializableHasSerialVersionUidFieldInspection() }

private fun <T : InspectionProfileEntry> jvmSdkInspectionFixture(
  projectFixture: TestFixture<LightJavaProject>,
  testDataPath: String?,
  inspection: () -> T,
): TestFixture<JvmSdkInspectionFixture<T>> = testFixture("jvmSdkInspectionFixture") {
  initialized(JvmSdkInspectionFixture(jvmInspectionFixture(projectFixture, testDataPath, inspection = inspection).init())) {}
}

/**
 * The fixture of a [DependencyInspection] test. It is the JUnit 5 form of [DependencyInspectionTestBase].
 * Create it with [dependencyInspectionFixture].
 */
@TestOnly
class DependencyInspectionFixture internal constructor(
  base: JvmInspectionFixture<DependencyInspection>,
) : JvmInspectionFixture<DependencyInspection>(base.fixture, base.inspection) {
  /**
   * The name of the Java file in the package `pkg.api`.
   */
  val javaFooFile: String = "JavaFoo.java"

  /**
   * The name of the Kotlin file in the package `pkg.api`.
   */
  val kotlinFooFile: String = "KotlinFoo.kt"

  /**
   * Adds [toFileText] as `pkg/client/<toFile>`, adds a rule that forbids the use of `pkg/api/<fromFile>` in it, and checks the highlighting.
   */
  fun dependencyViolationTest(fromFile: String, toFile: String, toFileText: String, skipImports: Boolean = false) {
    val dependencyValidationManager = DependencyValidationManager.getInstance(project)
    dependencyValidationManager.setSkipImportStatements(skipImports)
    addFileToProject("pkg/client/$toFile", toFileText)
    val fromScopeId = fromFile.substringBefore(".")
    val fromScope = dependencyValidationManager.getScope(fromScopeId) ?: NamedScope(
      fromScopeId, PackageSetFactory.getInstance().compile("file:pkg/api/$fromFile")
    )
    val toScope = NamedScope(toFile.substringBefore("."), PackageSetFactory.getInstance().compile("file:pkg/client/$toFile"))
    dependencyValidationManager.apply {
      addRule(DependencyRule(toScope, fromScope, true))
    }
    testHighlighting("pkg/client/$toFile")
  }
}

/**
 * Creates a [DependencyInspectionFixture] for one test. It adds `pkg/api/JavaFoo.java` and `pkg/api/KotlinFoo.kt`, as [DependencyInspectionTestBase] does.
 * After the test, it removes the dependency rules and restores the import option of the project.
 * Give it the project of [com.intellij.jvm.analysis.testFramework.jvmProjectFixture].
 */
@TestOnly
@JvmOverloads
fun dependencyInspectionFixture(
  projectFixture: TestFixture<LightJavaProject>,
  testDataPath: String? = null,
): TestFixture<DependencyInspectionFixture> = testFixture("dependencyInspectionFixture") {
  val dependency = DependencyInspectionFixture(jvmInspectionFixture(projectFixture, testDataPath) { DependencyInspection() }.init())
  dependency.addFileToProject("pkg/api/${dependency.javaFooFile}", """
      package pkg.api;

      public class JavaFoo() {
          public static void foo() { }
      };
    """.trimIndent())
  dependency.addFileToProject("pkg/api/${dependency.kotlinFooFile}", """
      package pkg.api

      public class KotlinFoo()
    """.trimIndent())
  val dependencyValidationManager = DependencyValidationManager.getInstance(dependency.project)
  val skipImports = dependencyValidationManager.skipImportStatements()
  initialized(dependency) {
    dependencyValidationManager.removeAllRules()
    dependencyValidationManager.setSkipImportStatements(skipImports)
  }
}

private class AnnotationsLibraryDescriptor(private val annotationClass: Class<*>) : ProjectDescriptor(LanguageLevel.HIGHEST) {
  override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
    super.configureModule(module, model, contentEntry)
    val jar = Path.of(PathUtil.getJarPathForClass(annotationClass))
    PsiTestUtil.addLibrary(model, "annotations", jar.parent.pathString, jar.name)
  }

  override fun equals(other: Any?): Boolean = other is AnnotationsLibraryDescriptor && other.annotationClass == annotationClass

  override fun hashCode(): Int = annotationClass.hashCode()
}

/**
 * Creates the project of a [MissingDeprecatedAnnotationOnScheduledForRemovalApiInspection] test class,
 * with the JetBrains annotations as the library `annotations`.
 */
@TestOnly
fun missingDeprecatedAnnotationOnScheduledForRemovalApiProjectFixture(): TestFixture<LightJavaProject> =
  lightJavaProjectFixture(AnnotationsLibraryDescriptor(ApiStatus.ScheduledForRemoval::class.java))

/**
 * Creates the fixture of a [MissingDeprecatedAnnotationOnScheduledForRemovalApiInspection] test.
 * It replaces [MissingDeprecatedAnnotationOnScheduledForRemovalApiInspectionTestBase].
 * Give it the project of [missingDeprecatedAnnotationOnScheduledForRemovalApiProjectFixture].
 */
@TestOnly
@JvmOverloads
fun missingDeprecatedAnnotationOnScheduledForRemovalApiInspectionFixture(
  projectFixture: TestFixture<LightJavaProject>,
  testDataPath: String? = null,
): TestFixture<JvmInspectionFixture<MissingDeprecatedAnnotationOnScheduledForRemovalApiInspection>> =
  jvmInspectionFixture(projectFixture, testDataPath) { MissingDeprecatedAnnotationOnScheduledForRemovalApiInspection() }

/**
 * Creates the project of a JVM test class with the language level [LanguageLevel.HIGHEST] and the JetBrains annotations,
 * as `ProjectDescriptor(LanguageLevel.HIGHEST, true)` gives it.
 */
@TestOnly
fun jvmAnnotationsProjectFixture(): TestFixture<LightJavaProject> =
  lightJavaProjectFixture(ProjectDescriptor(LanguageLevel.HIGHEST, true))

/**
 * Creates the fixture of a [MustAlreadyBeRemovedApiInspection] test. It replaces [MustAlreadyBeRemovedApiInspectionTestBase].
 * Give it the project of [jvmAnnotationsProjectFixture].
 */
@TestOnly
@JvmOverloads
fun mustAlreadyBeRemovedApiInspectionFixture(
  projectFixture: TestFixture<LightJavaProject>,
  testDataPath: String? = null,
): TestFixture<JvmInspectionFixture<MustAlreadyBeRemovedApiInspection>> =
  jvmInspectionFixture(projectFixture, testDataPath) { MustAlreadyBeRemovedApiInspection() }

private class ApiLibraryDescriptor(private val annotationClass: Class<*>, private val libraryPath: String) : ProjectDescriptor(LanguageLevel.HIGHEST) {
  override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
    super.configureModule(module, model, contentEntry)
    PsiTestUtil.addProjectLibrary(model, "annotations", listOf(PathUtil.getJarPathForClass(annotationClass)))
    PsiTestUtil.addProjectLibrary(model, "library", listOf(libraryPath))
  }

  override fun equals(other: Any?): Boolean =
    other is ApiLibraryDescriptor && other.annotationClass == annotationClass && other.libraryPath == libraryPath

  override fun hashCode(): Int = 31 * annotationClass.hashCode() + libraryPath.hashCode()
}

/**
 * Creates the project of a [NonExtendableApiInspection] test class.
 * The project has the JetBrains annotations and the library `library` with the root [testDataPath], as [NonExtendableApiInspectionTestBase] has.
 */
@TestOnly
fun nonExtendableApiProjectFixture(testDataPath: String): TestFixture<LightJavaProject> =
  lightJavaProjectFixture(ApiLibraryDescriptor(ApiStatus.NonExtendable::class.java, testDataPath))

/**
 * Creates the fixture of a [NonExtendableApiInspection] test. It replaces [NonExtendableApiInspectionTestBase].
 * Give it the project of [nonExtendableApiProjectFixture] with the same [testDataPath].
 */
@TestOnly
fun nonExtendableApiInspectionFixture(
  projectFixture: TestFixture<LightJavaProject>,
  testDataPath: String,
): TestFixture<JvmInspectionFixture<NonExtendableApiInspection>> =
  jvmInspectionFixture(projectFixture, testDataPath) { NonExtendableApiInspection() }

/**
 * Creates the project of an [OverrideOnlyApiInspection] test class.
 * The project has the JetBrains annotations and the library `library` with the root [testDataPath], as [OverrideOnlyApiInspectionTestBase] has.
 */
@TestOnly
fun overrideOnlyApiProjectFixture(testDataPath: String): TestFixture<LightJavaProject> =
  lightJavaProjectFixture(ApiLibraryDescriptor(ApiStatus.OverrideOnly::class.java, testDataPath))

/**
 * Creates the fixture of an [OverrideOnlyApiInspection] test. It replaces [OverrideOnlyApiInspectionTestBase].
 * Give it the project of [overrideOnlyApiProjectFixture] with the same [testDataPath].
 */
@TestOnly
fun overrideOnlyApiInspectionFixture(
  projectFixture: TestFixture<LightJavaProject>,
  testDataPath: String,
): TestFixture<JvmInspectionFixture<OverrideOnlyApiInspection>> =
  jvmInspectionFixture(projectFixture, testDataPath) { OverrideOnlyApiInspection() }

/**
 * Creates the fixture of an [ObsoleteApiUsageInspection] test. It replaces [ObsoleteApiUsageInspectionTestBase].
 * It adds the class `A` with an obsolete method, and it sets the level of the inspection to a warning.
 * Give it the project of [jvmAnnotationsProjectFixture].
 */
@TestOnly
@JvmOverloads
fun obsoleteApiUsageInspectionFixture(
  projectFixture: TestFixture<LightJavaProject>,
  testDataPath: String? = null,
): TestFixture<JvmInspectionFixture<ObsoleteApiUsageInspection>> = testFixture("obsoleteApiUsageInspectionFixture") {
  val obsolete = jvmInspectionFixture(projectFixture, testDataPath) { ObsoleteApiUsageInspection() }.init()
  obsolete.addClass("""
      import org.jetbrains.annotations.ApiStatus;

      public class A {
        @ApiStatus.Obsolete
        void f() {}
      }""".trimIndent()
  )
  obsolete.enableWarnings()
  initialized(obsolete) {}
}

/**
 * Creates the project of a [StringToUpperWithoutLocale2Inspection] test class, with the JetBrains annotations as the library `annotations`.
 */
@TestOnly
fun stringToUpperWithoutLocaleProjectFixture(): TestFixture<LightJavaProject> =
  lightJavaProjectFixture(AnnotationsLibraryDescriptor(NonNls::class.java))

/**
 * Creates the fixture of a [StringToUpperWithoutLocale2Inspection] test. It replaces [StringToUpperWithoutLocaleInspectionTestBase].
 * Give it the project of [stringToUpperWithoutLocaleProjectFixture].
 */
@TestOnly
@JvmOverloads
fun stringToUpperWithoutLocaleInspectionFixture(
  projectFixture: TestFixture<LightJavaProject>,
  testDataPath: String? = null,
): TestFixture<JvmInspectionFixture<StringToUpperWithoutLocale2Inspection>> =
  jvmInspectionFixture(projectFixture, testDataPath) { StringToUpperWithoutLocale2Inspection() }

/**
 * The fixture of a [SuppressionAnnotationInspection] test. It is the JUnit 5 form of [SuppressionAnnotationInspectionTestBase].
 * Create it with [suppressionAnnotationInspectionFixture], and give it the project of [jvmAnnotationsProjectFixture].
 */
@TestOnly
class SuppressionAnnotationInspectionFixture internal constructor(
  base: JvmInspectionFixture<SuppressionAnnotationInspection>,
) : JvmInspectionFixture<SuppressionAnnotationInspection>(base.fixture, base.inspection) {
  /**
   * Configures [code], runs the quick fix "Allow these suppressions", and checks that the inspection of the profile allows [ids].
   * It runs on the EDT.
   */
  fun testAllowSuppressionQuickFix(jvmLanguage: JvmLanguage, code: String, vararg ids: String) {
    runInEdtAndWait {
      InspectionProfileImpl.INIT_INSPECTIONS = true
      try {
        assertTrue(inspection.myAllowedSuppressions.isEmpty(), inspection.myAllowedSuppressions.toString())
        configureByText("A.${jvmLanguage.ext}", code)
        val intention = findSingleIntention("Allow these suppressions")
        val previewText = buildPreviewText(ids)
        checkIntentionPreviewHtml(intention, previewText)
        launchAction(intention)
        val inspection = InspectionProfileManager.getInstance(project).currentProfile
          .getUnwrappedTool("SuppressionAnnotation", file) as SuppressionAnnotationInspection
        assertTrue(inspection.myAllowedSuppressions.containsAll(ids.toList()), inspection.myAllowedSuppressions.toString())
      }
      finally {
        InspectionProfileImpl.INIT_INSPECTIONS = false
      }
    }
  }

  private fun buildPreviewText(ids: Array<out String>): String {
    val idsPart = ids.joinToString(separator = "") { "<option selected=\"selected\">$it</option>" }
    return "Ignored suppressions:<br/><br/><select multiple=\"multiple\" size=\"${ids.size + 2}\">$idsPart</select>"
  }
}

/**
 * Creates a [SuppressionAnnotationInspectionFixture] for one test. Give it the project of [jvmAnnotationsProjectFixture].
 */
@TestOnly
@JvmOverloads
fun suppressionAnnotationInspectionFixture(
  projectFixture: TestFixture<LightJavaProject>,
  testDataPath: String? = null,
): TestFixture<SuppressionAnnotationInspectionFixture> = testFixture("suppressionAnnotationInspectionFixture") {
  initialized(SuppressionAnnotationInspectionFixture(jvmInspectionFixture(projectFixture, testDataPath) { SuppressionAnnotationInspection() }.init())) {}
}

/**
 * Creates the fixture of a [SystemGetPropertyInspection] test. It replaces [SystemGetPropertyInspectionTestBase].
 * Give it the project of [com.intellij.jvm.analysis.testFramework.jvmProjectFixture].
 */
@TestOnly
@JvmOverloads
fun systemGetPropertyInspectionFixture(
  projectFixture: TestFixture<LightJavaProject>,
  testDataPath: String? = null,
): TestFixture<JvmInspectionFixture<SystemGetPropertyInspection>> =
  jvmInspectionFixture(projectFixture, testDataPath) { SystemGetPropertyInspection() }

/**
 * Creates the fixture of an [UnstableApiUsageInspection] test. It replaces [UnstableApiUsageInspectionTestBase].
 * It adds the experimental, the scheduled for removal and the internal API of the old base.
 * Give it the project of [jvmAnnotationsProjectFixture].
 */
@TestOnly
@JvmOverloads
fun unstableApiUsageInspectionFixture(
  projectFixture: TestFixture<LightJavaProject>,
  testDataPath: String? = null,
): TestFixture<JvmInspectionFixture<UnstableApiUsageInspection>> = testFixture("unstableApiUsageInspectionFixture") {
  val unstable = jvmInspectionFixture(projectFixture, testDataPath) { UnstableApiUsageInspection() }.init()
  // otherwise assertion in PsiFileImpl ("Access to tree elements not allowed") will not pass
  unstable.allowTreeAccessForAllFiles()
  addUnstableApi(unstable)
  initialized(unstable) {}
}

/**
 * The fixture of a test of the automatic test method renamer. It is the JUnit 5 form of [JvmAutomaticTestMethodRenamerTestBase].
 * Create it with [jvmAutomaticTestMethodRenamerFixture].
 */
@TestOnly
class JvmAutomaticTestMethodRenamerFixture internal constructor(
  base: JvmCodeInsightFixture,
) : JvmCodeInsightFixture(base.fixture) {
  /**
   * Configures `<test name>/test_src/<filename>`, and checks that no automatic renamer applies to the element at the caret.
   */
  fun doTest(filename: String) {
    val filePath = "${getTestName(true)}/test_src/$filename"
    configureByFile(filePath)
    runReadActionBlocking {
      val element = elementAtCaret
      AutomaticRenamerFactory.EP_NAME.extensionList.forEach {
        assertFalse(it.isApplicable(element))
      }
    }
  }
}

/**
 * Creates a [JvmAutomaticTestMethodRenamerFixture] for one test. Give it the project of [com.intellij.jvm.analysis.testFramework.jvmProjectFixture].
 *
 * Each test gets a new module, as in [JvmAutomaticTestMethodRenamerTestBase].
 * The module has the JUnit 5 API and the test source root `<test name>/test_src`.
 */
@TestOnly
fun jvmAutomaticTestMethodRenamerFixture(
  projectFixture: TestFixture<LightJavaProject>,
  testDataPath: String,
): TestFixture<JvmAutomaticTestMethodRenamerFixture> = testFixture("jvmAutomaticTestMethodRenamerFixture") {
  val base = jvmCodeInsightFixture(projectFixture, testDataPath) { testName ->
    TestMethodRenamerDescriptor(PlatformTestUtil.lowercaseFirstLetter(testName, true))
  }.init()
  initialized(JvmAutomaticTestMethodRenamerFixture(base)) {}
}

// Not equal to any other descriptor, so each test gets a new module, as with the anonymous descriptor of the old base.
@Suppress("LightProjectDescriptorEqualsHashCode")
private class TestMethodRenamerDescriptor(private val testName: String) : DefaultLightProjectDescriptor() {
  override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
    super.configureModule(module, model, contentEntry)
    val jar1 = Path.of(PathUtil.getJarPathForClass(Nested::class.java))
    PsiTestUtil.addLibrary(model, "JUnit", jar1.parent.pathString, jar1.name)
    contentEntry.addSourceFolder(contentEntry.url + "/$testName/test_src", true)
  }
}
