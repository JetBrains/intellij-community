package library

import org.jetbrains.annotations.ApiStatus.OverrideOnly

@OverrideOnly
abstract class KotlinClassOverrideOnly {

  abstract fun overrideOnlyMethod(): Unit

  companion object {
    @JvmStatic
    fun staticMethod() {}
  }
}
