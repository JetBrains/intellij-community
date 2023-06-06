plugins {
    `kotlin-dsl`
}

group = "org.jetbrains.jewel"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation(libs.javaSarif)
}

gradlePlugin {
    plugins {
        register("merge-sarif") {
            id = "org.jetbrains.jewel.sarif"
            implementationClass = "org.jetbrains.jewel.buildlogic.sarif.MergeSarifPlugin"
        }
    }
}
kotlin.target.compilations.all {
    kotlinOptions {
        jvmTarget = "17"
    }
}
