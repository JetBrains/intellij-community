import java.net.URI
import org.jetbrains.intellij.platform.gradle.attributes.ComposedJarRule

plugins {
    jewel
    `jewel-check-public-api`
    alias(libs.plugins.composeDesktop)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ideaPluginBase)
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
    api(projects.markdown.core)
    api(projects.ideLafBridge)
    compileOnly(libs.coil.compose)
    compileOnly(libs.coil.network.ktor3)
    compileOnly(projects.markdown.extensions.frontMatter)
    compileOnly(projects.markdown.extensions.gfmAlerts)
    compileOnly(projects.markdown.extensions.gfmTables)
    compileOnly(projects.markdown.extensions.images)

    intellijPlatform { intellijIdea(libs.versions.idea) }

    testImplementation(compose.desktop.uiTestJUnit4)

    // ide-laf-bridge requests a composed-jar from the ui module, which publishes a plain jar. Only the
    // IntelliJ Platform module plugin registers the rule that accepts it. Remove this when the base plugin does too.
    attributesSchema {
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE) { compatibilityRules.add(ComposedJarRule::class) }
    }
}
