import org.gradle.api.Project

enum class SupportedIJVersion {
    IJ_232,
    IJ_233
}

fun Project.supportedIJVersion(): SupportedIJVersion {
    val prop = localProperty("supported.ij.version")
        ?: rootProject.property("supported.ij.version")
        ?: error(
            "'supported.ij.version' gradle property is missing. " +
                "Please, provide it using local.properties file or -Psupported.ij.version argument in CLI"
        )

    return when (prop) {
        "232" -> SupportedIJVersion.IJ_232
        "233" -> SupportedIJVersion.IJ_233
        else -> {
            error(
                "Invalid 'supported.ij.version' with value '$prop' is provided. " +
                    "It should be in set of these values: ('232', '233')"
            )
        }
    }
}