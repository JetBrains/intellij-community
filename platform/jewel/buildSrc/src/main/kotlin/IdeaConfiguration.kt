import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.jvm.toolchain.JavaLanguageVersion
import java.util.concurrent.atomic.AtomicBoolean

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
                No 'supported.ij.version' property provided. Falling back to IJ 241.
                It is recommended to provide it using the local.properties file or 
                -Psupported.ij.version to avoid unexpected behavior.
                """.trimIndent()
            )
        }
        return SupportedIJVersion.IJ_241
    }

    return when (prop) {
        "232" -> SupportedIJVersion.IJ_232
        "233" -> SupportedIJVersion.IJ_233
        "241" -> SupportedIJVersion.IJ_241
        else -> error(
            "Invalid 'supported.ij.version' with value '$prop' is provided. " +
                "It should be one of these values: " +
                SupportedIJVersion.values().joinToString(", ") { it.rawPlatformVersion }
        )
    }
}

@Suppress("unused") // Used to set the Java toolchain version
fun Property<JavaLanguageVersion>.assign(version: Int) =
    set(JavaLanguageVersion.of(version))
