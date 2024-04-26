package plugin;

import library.JavaClass
import library.KotlinClass


class JavaInheritor : JavaClass() {
  lateinit var javaDelegate : JavaClass

  override fun overrideOnlyMethod() {
    super.overrideOnlyMethod()
    javaDelegate.overrideOnlyMethod()
  }

  @Suppress("UNUSED_PARAMETER")
  fun overrideOnlyMethod(x: Int) {
    super.<warning descr="Method 'overrideOnlyMethod()' can only be overridden">overrideOnlyMethod</warning>()
    javaDelegate.<warning descr="Method 'overrideOnlyMethod()' can only be overridden">overrideOnlyMethod</warning>()
  }

  fun notOverrideOnlyMethod() {
    super.<warning descr="Method 'overrideOnlyMethod()' can only be overridden">overrideOnlyMethod</warning>()
    javaDelegate.<warning descr="Method 'overrideOnlyMethod()' can only be overridden">overrideOnlyMethod</warning>()
  }
}

class KotlinInheritor : KotlinClass() {
  lateinit var kotlinDelegate : KotlinClass

  override fun overrideOnlyMethod() {
    super.overrideOnlyMethod()
    kotlinDelegate.overrideOnlyMethod()
  }

  @Suppress("UNUSED_PARAMETER")
  fun overrideOnlyMethod(x: Int) {
    super.<warning descr="Method 'overrideOnlyMethod()' can only be overridden">overrideOnlyMethod</warning>()
    kotlinDelegate.<warning descr="Method 'overrideOnlyMethod()' can only be overridden">overrideOnlyMethod</warning>()
  }

  fun notOverrideOnlyMethod() {
    super.<warning descr="Method 'overrideOnlyMethod()' can only be overridden">overrideOnlyMethod</warning>()
    kotlinDelegate.<warning descr="Method 'overrideOnlyMethod()' can only be overridden">overrideOnlyMethod</warning>()
  }
}