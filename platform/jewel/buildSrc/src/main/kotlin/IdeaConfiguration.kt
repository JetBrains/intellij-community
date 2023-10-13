import java.util.concurrent.atomic.AtomicBoolean
import org.gradle.api.Project

enum class SupportedIJVersion {
    IJ_232,
    IJ_233
}

private var warned = AtomicBoolean(false)

fun Project.supportedIJVersion(): SupportedIJVersion {
    val prop = kotlin.runCatching {
        localProperty("supported.ij.version")
            ?: rootProject.property("supported.ij.version")?.toString()
    }.getOrNull()

    if (prop == null) {
        if (!warned.getAndSet(true)) {
            logger.warn(
                """
                    No 'supported.ij.version' property provided. Falling back to IJ 233.
                    It is recommended to provide it using local.properties file or -Psupported.ij.version to avoid unexpected behavior.
                """.trimIndent()
            )
        }
        return SupportedIJVersion.IJ_233
    }

    return when (prop) {
        "232" -> SupportedIJVersion.IJ_232
        "233" -> SupportedIJVersion.IJ_233
        else -> error(
            "Invalid 'supported.ij.version' with value '$prop' is provided. " +
                "It should be in set of these values: ('232', '233')"
        )
    }
}
