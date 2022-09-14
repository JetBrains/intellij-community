pluginManagement {
    val jvmWrapperVersion: String by settings
    plugins {
        id("me.filippov.gradle.jvm.wrapper") version jvmWrapperVersion apply false
    }
}

rootProject.name = "TestProject"
