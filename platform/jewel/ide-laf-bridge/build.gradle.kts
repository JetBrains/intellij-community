import java.net.URI
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    jewel
    `jewel-check-public-api`
    alias(libs.plugins.composeDesktop)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ideaPluginModule)
    alias(libs.plugins.kotlinx.serialization)
}

// Because we need to define IJP dependencies, the dependencyResolutionManagement
// from settings.gradle.kts is overridden and we have to redeclare everything here.
repositories {
    google()
    mavenCentral()

    intellijPlatform {
        ivy {
            name = "PKGS IJ Snapshots"
            url = URI("https://cache-redirector.jetbrains.com/files/p/kpm/public/idea/snapshots/")
            patternLayout {
                artifact("[module]-[revision](-[classifier]).[ext]")
                artifact("[module]-[revision](.[classifier]).[ext]")
            }
            metadataSources { artifact() }
        }

        defaultRepositories()
    }
}

dependencies {
    api(projects.ui) { exclude(group = "org.jetbrains.kotlinx") }
    intellijPlatform {
        intellijIdea(libs.versions.idea)
        testFramework(TestFrameworkType.Platform)

        bundledPlugin("org.jetbrains.plugins.textmate")
        testBundledPlugin("com.intellij.xml")
    }

    implementation(libs.kotlinx.serialization.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(compose.desktop.uiTestJUnit4) { excludeCoroutines() }
    testImplementation(libs.mockk) { excludeCoroutines() }
    testImplementation(compose.desktop.currentOs) {
        exclude(group = "org.jetbrains.compose.material")
        excludeCoroutines()
    }
    testRuntimeOnly(libs.intellijPlatform.java.rt)
}

fun ModuleDependency.excludeCoroutines() {
    exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core")
    exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core-jvm")
}

sourceSets { test { kotlin { srcDirs("ide-laf-bridge-tests/src/test/kotlin") } } }

// IntelliJ Platform's product-info.json injects -Dskiko.library.path pointing at the IDE's bundled Skiko binaries,
// which can mismatch the compose.desktop.currentOs Skiko version on the test classpath.
class StripSkikoLibraryPathArgumentProvider(
    @get:Nested val delegate: CommandLineArgumentProvider,
) : CommandLineArgumentProvider {
    override fun asArguments(): Iterable<String> =
        delegate.asArguments().filterNot { it.startsWith("-Dskiko.library.path=") }
}

fun Test.stripSkikoLibraryPath() {
    systemProperties.remove("skiko.library.path")
    val wrappedProviders =
        jvmArgumentProviders.map {
            it as? StripSkikoLibraryPathArgumentProvider ?: StripSkikoLibraryPathArgumentProvider(it)
        }
    jvmArgumentProviders.clear()
    jvmArgumentProviders.addAll(wrappedProviders)
}

tasks.withType<Test>().configureEach {
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    stripSkikoLibraryPath()
}

afterEvaluate { tasks.withType<Test>().configureEach { stripSkikoLibraryPath() } }
