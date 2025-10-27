package fleet.buildtool.authentication

import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.ExperimentalXmlUtilApi
import nl.adaptivity.xmlutil.serialization.XML
import nl.adaptivity.xmlutil.serialization.XmlConfig
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import org.slf4j.Logger
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText

const val intellijPrivateDependenciesId = "intellij-private-dependencies"

val defaultSettingsPath by lazy {
  Path.of(System.getProperty("user.home")).resolve(".m2/settings.xml")
}

@OptIn(ExperimentalXmlUtilApi::class)
fun readMavenSettingsServerCredentials(
  logger: Logger,
  mavenSettingsPath: Path = defaultSettingsPath,
): List<MavenServerCredentials> {
  logger.info("Reading servers credentials from Maven settings '$mavenSettingsPath'")
  val xml = XML {
    defaultPolicy {
      unknownChildHandler = XmlConfig.IGNORING_UNKNOWN_CHILD_HANDLER
    }
  }
  if (!mavenSettingsPath.exists()) {
    logger.warn("WARNING: failed to find '$mavenSettingsPath', no repository credentials will be read")
    return emptyList()
  }
  return try {
    xml.decodeFromString(MavenSettings.serializer(), mavenSettingsPath.readText()).servers?.servers ?: emptyList()
  }
  catch (e: Exception) {
    logger.warn("WARNING: failed to parse {}", mavenSettingsPath, e)
    emptyList()
  }
}

@Serializable
@XmlSerialName(
  value = "settings",
  namespace = "http://maven.apache.org/SETTINGS/1.0.0",
)
private data class MavenSettings(@XmlElement val servers: MavenServers?)

@Serializable
@XmlSerialName("servers")
data class MavenServers(@XmlElement val servers: List<MavenServerCredentials>?)

@Serializable
@XmlSerialName("server")
data class MavenServerCredentials(
  @XmlElement @XmlSerialName("id") val id: String?,
  @XmlElement @XmlSerialName("username") val username: String?,
  @XmlElement @XmlSerialName("password") val password: String?,
)
