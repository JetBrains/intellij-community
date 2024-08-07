package plugin.kotlin

import library.JavaClass
import library.JavaClassOverrideOnly
import library.JavaInterface
import library.JavaInterfaceOverrideOnly
import library.KotlinClass
import library.KotlinClassOverrideOnly
import library.KotlinInterface
import library.KotlinInterfaceOverrideOnly

class Invoker {
  fun invocations(
    javaClass: JavaClass,
    javaInterface: JavaInterface,
    kotlinClass: KotlinClass,
    kotlinInterface: KotlinInterface,

    javaClassOverrideOnly: JavaClassOverrideOnly,
    javaInterfaceOverrideOnly: JavaInterfaceOverrideOnly,
    kotlinClassOverrideOnly: KotlinClassOverrideOnly,
    kotlinInterfaceOverrideOnly: KotlinInterfaceOverrideOnly
  ) {
    javaClass.<warning descr="Method 'overrideOnlyMethod()' can only be overridden">overrideOnlyMethod</warning>()
    javaInterface.<warning descr="Method 'implementOnlyMethod()' can only be overridden">implementOnlyMethod</warning>()
    kotlinClass.<warning descr="Method 'overrideOnlyMethod()' can only be overridden">overrideOnlyMethod</warning>()
    kotlinInterface.<warning descr="Method 'implementOnlyMethod()' can only be overridden">implementOnlyMethod</warning>()

    javaClassOverrideOnly.<warning descr="Method 'overrideOnlyMethod()' can only be overridden">overrideOnlyMethod</warning>()
    javaInterfaceOverrideOnly.<warning descr="Method 'implementOnlyMethod()' can only be overridden">implementOnlyMethod</warning>()
    kotlinClassOverrideOnly.<warning descr="Method 'overrideOnlyMethod()' can only be overridden">overrideOnlyMethod</warning>()
    kotlinInterfaceOverrideOnly.<warning descr="Method 'implementOnlyMethod()' can only be overridden">implementOnlyMethod</warning>()
  }

  @Suppress("UNUSED_VARIABLE")
  fun methodReferences() {
    val a = JavaClass::<warning descr="Method 'overrideOnlyMethod()' can only be overridden">overrideOnlyMethod</warning>
    val b = JavaInterface::<warning descr="Method 'implementOnlyMethod()' can only be overridden">implementOnlyMethod</warning>

    val a1 = JavaClassOverrideOnly::<warning descr="Method 'overrideOnlyMethod()' can only be overridden">overrideOnlyMethod</warning>
    val b1 = JavaInterfaceOverrideOnly::<warning descr="Method 'implementOnlyMethod()' can only be overridden">implementOnlyMethod</warning>

    val c = KotlinClass::<warning descr="Method 'overrideOnlyMethod()' can only be overridden">overrideOnlyMethod</warning>
    val d = KotlinInterface::<warning descr="Method 'implementOnlyMethod()' can only be overridden">implementOnlyMethod</warning>

    val c1 = KotlinClassOverrideOnly::<warning descr="Method 'overrideOnlyMethod()' can only be overridden">overrideOnlyMethod</warning>
    val d1 = KotlinInterfaceOverrideOnly::<warning descr="Method 'implementOnlyMethod()' can only be overridden">implementOnlyMethod</warning>
  }
}

class JavaInheritor : JavaClass() {
  //No warning
  override fun overrideOnlyMethod() {
  }
}

class JavaImplementor : JavaInterface {
  //No warning
  override fun implementOnlyMethod() {}
}

class KotlinInheritor : KotlinClass() {
  //No warning
  override fun overrideOnlyMethod() {
  }
}

class KotlinImplementor : KotlinInterface {
  //No warning
  override fun implementOnlyMethod() {}
}

class JavaInheritorOverrideOnly : JavaClassOverrideOnly() {
  //No warning
  override fun overrideOnlyMethod() {
  }
}

class JavaImplementorOverrideOnly : JavaInterfaceOverrideOnly {
  //No warning
  override fun implementOnlyMethod() {}
}

class KotlinInheritorOverrideOnly : KotlinClassOverrideOnly() {
  //No warning
  override fun overrideOnlyMethod() {
  }
}

class KotlinImplementorOverrideOnly : KotlinInterfaceOverrideOnly {
  //No warning
  override fun implementOnlyMethod() {}
}
