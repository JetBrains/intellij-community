plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    {{ kts_kotlin_plugin_repositories }}
    google()
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:{{kgp_version}}")
}
