package library

import org.jetbrains.annotations.ApiStatus.OverrideOnly

@OverrideOnly
interface KotlinInterfaceOverrideOnly {

  fun implementOnlyMethod(): Unit

  companion object {
    @JvmStatic
    fun staticMethod() {}
  }
}
