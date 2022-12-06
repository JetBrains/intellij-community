package org.jetbrains.jewel.util.font

import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import org.jetbrains.jewel.util.isLinux
import org.jetbrains.jewel.util.isMacOs
import org.jetbrains.jewel.util.isWindows
import java.io.File
import java.util.TreeMap
import java.util.zip.ZipFile
import kotlin.io.path.createTempFile
import kotlin.io.path.inputStream
import kotlin.io.path.readLines

// Note: TTC (TrueType Collection) support in AWT is pretty abysmal — it will load them, but
// only the first entry in the ttc file will ever be available.
val supportedFontFileExtensions = listOf("ttf", "otf", "ttc")

@OptIn(FlowPreview::class)
private val DEFAULT_LINUX_FONTS
    get() = flowOf("/usr/share/fonts", "/usr/local/share/fonts", "${System.getProperty("user.home")}/.fonts")
        .map { File(it) }
        .flatMapMerge { it.walkTopDown().asFlow() }
        .filter { supportedFontFileExtensions.contains(it.extension.lowercase()) }
        .asFileProviderFlow(FileProvider.Origin.FILESYSTEM)

@OptIn(FlowPreview::class)
private val DEFAULT_MACOS_FONTS
    get() = flowOf("/Library/Fonts", "/System/Library/Fonts")
        .map { File(it) }
        .flatMapMerge { it.walkTopDown().asFlow() }
        .filter { supportedFontFileExtensions.contains(it.extension.lowercase()) }
        .asFileProviderFlow(FileProvider.Origin.FILESYSTEM)

private val DEFAULT_WINDOWS_FONTS
    get() = File(" C:\\Windows\\Fonts")
        .walkTopDown()
        .asFlow()
        .filter { supportedFontFileExtensions.contains(it.extension.lowercase()) }
        .asFileProviderFlow(FileProvider.Origin.FILESYSTEM)

@OptIn(ExperimentalCoroutinesApi::class)
fun getAvailableFontFiles(): Flow<FileProvider> {
    val osSpecificFonts = when {
        isLinux() -> merge(DEFAULT_LINUX_FONTS, getLinuxFontsUsingFcList())
        isWindows() -> merge(DEFAULT_WINDOWS_FONTS, getWindowsFontsUsingRegistry())
        isMacOs() -> merge(DEFAULT_MACOS_FONTS, getMacOSFontsUsingSystemProfiler())
        else -> error("Unsupported OS: ${System.getProperty("os.name")}")
    }
    return merge(osSpecificFonts, getClasspathFonts())
}

private const val WINDOWS_FONTS_KEY_PATH = "SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion\\Fonts"

// Current limitations:
//  * If a font has a different "real" family name (as reported by AWT) from the name it appears with
//    in the registry, that font will not be matched, and thus won't be listed
//  * Font substitutions and "system" fonts (like Monospaced, SansSerif, etc.) aren't listed — but the
//    former are available as FontFamily.Monospaced, FontFamily.SansSerif, etc. at least
private fun getWindowsFontsUsingRegistry(): Flow<FileProvider> {
    @Suppress("UNCHECKED_CAST")
    val registryMap =
        Advapi32Util.registryGetValues(WinReg.HKEY_LOCAL_MACHINE, WINDOWS_FONTS_KEY_PATH) as TreeMap<String, String>

    val fontsDir = File("${System.getenv("WINDIR")}\\Fonts")

    // AWT doesn't know how to handle ttc files correctly — it only ever loads the first font in a ttc.
    // So, when we find a ttc entry with more than one font defined, we just get the first entry, hoping
    // that the order is the same as inside the ttc. Not that we have any control over this anyway!
    return registryMap.values.asFlow()
        .map { if (it.contains('\\')) File(it) else File(fontsDir, it) }
        .filter { it.exists() && supportedFontFileExtensions.contains(it.extension.lowercase()) }
        .asFileProviderFlow(FileProvider.Origin.SYSTEM_API)
}

private fun getLinuxFontsUsingFcList(): Flow<FileProvider> {
    val file = createTempFile()
    ProcessBuilder("fc-list")
        .redirectOutput(file.toFile())
        .start()
        .waitFor()

    return file.readLines()
        .asFlow()
        .map { File(it) }
        .filter { it.exists() && supportedFontFileExtensions.contains(it.extension.lowercase()) }
        .asFileProviderFlow(FileProvider.Origin.SYSTEM_API)
}

private val json = Json { ignoreUnknownKeys = true }

@OptIn(ExperimentalSerializationApi::class)
private fun getMacOSFontsUsingSystemProfiler(): Flow<FileProvider> {
    val file = createTempFile()
    ProcessBuilder("system_profiler", "-json", "SPFontsDataType")
        .redirectOutput(file.toFile())
        .start()
        .waitFor()

    val fontListingOutput = file.inputStream()
        .use { json.decodeFromStream<MacOsSystemProfilerFontListingOutput>(it) }

    return fontListingOutput.fontData.asFlow()
        .mapNotNull { fontData -> File(fontData.path).takeIf { it.exists() } }
        .asFileProviderFlow(FileProvider.Origin.SYSTEM_API)
}

/**
 * Scans the classpath for supported font files.
 *
 * @return A flow with all font files found.
 * @see supportedFontFileExtensions
 */
@OptIn(FlowPreview::class)
fun getClasspathFonts() =
    System.getProperty("java.class.path", ".")
        .split(System.getProperty("path.separator").toRegex())
        .asFlow()
        .map { File(it) }
        .flatMapMerge {
            if (it.isDirectory) {
                it.walkTopDown().asFlow().asFileProviderFlow(FileProvider.Origin.CLASSPATH)
            } else {
                zipFileFlow(it)
            }
        }
        .filter { it.extension.lowercase() in supportedFontFileExtensions }

private fun zipFileFlow(file: File) = flow {
    val zip = withContext(Dispatchers.IO) { ZipFile(file) }

    zip.entries().asSequence().forEach { zipEntry ->
        val name = zipEntry.name.substringBeforeLast(".")
        val extension = zipEntry.name.substringAfterLast(".")

        val path = "${file.absolutePath}${File.separator}$name.$extension"
        val fileProvider = FileProvider(name, extension, path, FileProvider.Origin.CLASSPATH) {
            val tmpFile = createTempFile().toFile()
            tmpFile.outputStream().use { output ->
                zip.getInputStream(zipEntry).use { input ->
                    input.transferTo(output)
                }
            }
            tmpFile
        }

        emit(fileProvider)
    }
    withContext(Dispatchers.IO) { zip.close() }
}
