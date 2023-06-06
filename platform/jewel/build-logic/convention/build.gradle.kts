plugins {
    `kotlin-dsl`
}

group = "org.jetbrains.jewel"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation(libs.kotlin.gradlePlugin)
    implementation(libs.kotlinter.gradlePlugin)
    implementation(libs.detekt.gradlePlugin)

    // Enables using type-safe accessors to reference plugins from the [plugins] block defined in version catalogs.
    // Context: https://github.com/gradle/gradle/issues/15383#issuecomment-779893192
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
}

gradlePlugin {
    plugins {
        register("jewelKotlin") {
            id = "org.jetbrains.jewel.kotlin"
            implementationClass = "org.jetbrains.jewel.buildlogic.convention.JewelKotlinPlugin"
        }
        register("jewelDetekt") {
            id = "org.jetbrains.jewel.detekt"
            implementationClass = "org.jetbrains.jewel.buildlogic.convention.JewelDetektPlugin"
        }
        register("jewelKtlint") {
            id = "org.jetbrains.jewel.ktlint"
            implementationClass = "org.jetbrains.jewel.buildlogic.convention.JewelKtlintPlugin"
        }
    }

}
kotlin.target.compilations.all {
    kotlinOptions {
        jvmTarget = "17"
    }
}
