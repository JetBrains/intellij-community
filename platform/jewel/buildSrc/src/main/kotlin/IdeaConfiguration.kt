import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.jvm.toolchain.JavaLanguageVersion
import java.util.concurrent.atomic.AtomicBoolean

enum class SupportedIJVersion {
    IJ_232,
    IJ_233
}

private var warned = AtomicBoolean(false)

fun Project.supportedIJVersion(): SupportedIJVersion {
    val prop = kotlin.runCatching {
        rootProject.findProperty("supported.ij.version")?.toString()
            ?: localProperty("supported.ij.version")
    }.getOrNull()

    if (prop == null) {
        if (!warned.getAndSet(true)) {
            logger.warn(
                """
                No 'supported.ij.version' property provided. Falling back to IJ 233.
                It is recommended to provide it using the local.properties file or 
                -Psupported.ij.version to avoid unexpected behavior.
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
                "It should be one of these values: ('232', '233')"
        )
    }
}

@Suppress("unused") // Used to set the Java toolchain version
fun Property<JavaLanguageVersion>.assign(version: Int) =
    set(JavaLanguageVersion.of(version))
