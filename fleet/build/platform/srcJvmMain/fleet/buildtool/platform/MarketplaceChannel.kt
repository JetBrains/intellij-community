package fleet.buildtool.platform

// TODO: maybe this needs its own module instead

enum class MarketplaceChannel(val value: String) {
  Stable("stable"),
  Nightly("nightly");

  override fun toString(): String = value

  companion object {
    private val byValues = MarketplaceChannel.values().associateBy { it.value }
    fun fromString(s: String): MarketplaceChannel? = byValues[s]
  }
}
