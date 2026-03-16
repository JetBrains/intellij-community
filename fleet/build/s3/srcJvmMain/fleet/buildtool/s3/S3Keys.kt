package fleet.buildtool.s3

import fleet.buildtool.platform.Platform
import fleet.buildtool.platform.toS3DistributionSlug

sealed class S3Version {
  abstract override fun toString(): String

  class Specific(val version: String) : S3Version() {
    override fun toString() = version
  }

  /**
   * Used for local testing only, artefacts using this version should never be used in production code.
   * This is a mitigation until we have some of the logic described in https://youtrack.jetbrains.com/issue/FL-18599
   */
  object Latest : S3Version() {
    override fun toString() = "latest"
  }
}

private const val partsPrefix = "fleet-parts"

fun Platform.fleetFleetDockS3Key(s3Version: S3Version): String = fleetPartS3Key(s3Version, "fleet-dock", { ".tar.zst" })
fun Platform.fleetFleetJBRS3Key(s3Version: S3Version): String = fleetPartS3Key(s3Version, "fleet-jbr", { ".tar.zst" }, archiveName = "jbr")

fun Platform.fleetPartS3Key(
  s3Version: S3Version,
  partName: String,
  extension: () -> String,
  archiveName: String = partName,
) = fleetPartS3Key(
  s3Version = s3Version,
  partName = partName,
  extension = extension,
  distributionSlug = "${toS3DistributionSlug()}/",
  archiveName = archiveName,
)

fun fleetPartS3Key(
  s3Version: S3Version,
  partName: String,
  extension: () -> String,
  archiveName: String = partName,
): String = fleetPartS3Key(
  s3Version,
  partName,
  extension,
  distributionSlug = "",
  archiveName = archiveName,
)

fun fleetPartS3Key(
  s3Version: S3Version,
  partName: String,
  extension: () -> String,
  distributionSlug: String,
  archiveName: String = partName,
): String = when (s3Version) {
  S3Version.Latest -> "fleet-parts/latest/$partName/$distributionSlug$archiveName-latest${extension()}"
  is S3Version.Specific -> "$partsPrefix/$partName/$distributionSlug$archiveName-${s3Version.version}${extension()}"
}

