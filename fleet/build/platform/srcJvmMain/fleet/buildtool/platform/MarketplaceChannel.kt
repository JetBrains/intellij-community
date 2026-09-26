package fleet.buildtool.platform

// TODO: maybe this needs its own module instead

enum class MarketplaceChannel(val value: String) {
  Stable("stable"),
  Nightly("nightly");

  override fun toString(): String = value
}
