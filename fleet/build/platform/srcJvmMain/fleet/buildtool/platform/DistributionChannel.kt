package fleet.buildtool.platform

enum class DistributionChannel {
  EAP, EAPCandidate, Nightly, Unspecified;

  companion object {
    fun fromString(s: String?): DistributionChannel = when (s) {
      "nightly" -> Nightly
      "stable",
      "eap",
        -> EAP
      "eap_candidate" -> EAPCandidate
      "", null -> Unspecified
      else -> error("unsupported channel string '$s'")
    }
  }
}

fun DistributionChannel.toMarketplaceChannel(): MarketplaceChannel = when (this) {
  DistributionChannel.EAP,
  DistributionChannel.EAPCandidate,
    -> MarketplaceChannel.Stable
  DistributionChannel.Nightly -> MarketplaceChannel.Nightly
  DistributionChannel.Unspecified -> MarketplaceChannel.Nightly // defaults to Nightly when channel of Fleet is unspecified
}

fun DistributionChannel.toFeedChannel(): String = when (this) {
  DistributionChannel.EAP -> "eap"
  DistributionChannel.EAPCandidate -> "eap_candidate"
  DistributionChannel.Nightly -> "nightly"
  DistributionChannel.Unspecified -> error("cannot resolve feed channel from an unspecified distribution channel")
}
