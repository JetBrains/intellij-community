import java.io.ByteArrayOutputStream

plugins {
    alias(libs.plugins.composeDesktop) apply false
    alias(libs.plugins.compose.compiler) apply false
    `jewel-linting`
}

tasks {
    register<Delete>("clean") { delete(rootProject.layout.buildDirectory) }

    wrapper { distributionType = Wrapper.DistributionType.ALL }
}
