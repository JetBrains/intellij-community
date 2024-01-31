enum class SupportedIJVersion(
    /** Human-readable major platform version. E.g., `2023.3` */
    val majorPlatformVersion: String,
    /** Raw major platform version. E.g., `233` */
    val rawPlatformVersion: String,
    /** If true, this platform version has its dedicated bridge artifact. */
    val hasBridgeArtifact: Boolean,
    /** The JDK version to use. */
    val jdkVersion: Int,
) {

    IJ_232("2023.2", "232", true, 17),
    IJ_233("2023.3", "233", true, 17),
    IJ_241("2024.1", "241", false, 17),
}
