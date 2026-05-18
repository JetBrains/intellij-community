plugins {
    alias(libs.plugins.android.kotlin.multiplatform.library)
    kotlin("multiplatform")
    alias(libs.plugins.compose.compiler)
    id("test.convention")
}

repositories {
    {{ kts_kotlin_plugin_repositories }}
    google()
    mavenCentral()
}

kotlin {
    jvm()

    androidLibrary {
        namespace = "test.compose.mpp"
        compileSdk = 36
        minSdk = 30
    }

    sourceSets {
        val commonMain by getting
        val jvmAndroidMain by creating {
            dependsOn(commonMain)
        }
        val androidMain by getting {
            dependsOn(jvmAndroidMain)
            dependencies {
                api(libs.androidx.compose.runtime)
                implementation(libs.androidx.compose.material3)
            }
        }
        val jvmMain by getting {
            dependsOn(jvmAndroidMain)
            dependencies {
                api(libs.androidx.compose.runtime.desktop)
                implementation(libs.compose.material3.desktop)
                implementation(libs.compose.runtime.desktop)
            }
        }
    }
}
