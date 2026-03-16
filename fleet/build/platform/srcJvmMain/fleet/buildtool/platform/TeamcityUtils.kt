package fleet.buildtool.platform

const val TEAMCITY_VERSION = "TEAMCITY_VERSION"

fun isRunningOnTeamcity(): Boolean = System.getenv(TEAMCITY_VERSION)?.isNotBlank() ?: false
