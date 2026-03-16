package fleet.buildtool.license

import kotlinx.serialization.Serializable

fun thirdPartyLicenseFilename(
  productName: String,
  version: String,
  extension: String,
): String = "$productName-$version-third-party-libraries${extension}"

@Serializable
data class JetbrainsLicenceEntry(
  val name: String,
  val version: String?,
  val url: String,
  val license: String?,
  val licenseUrl: String?,
)
