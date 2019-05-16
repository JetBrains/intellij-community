package plugin.kotlin

import library.JavaClass
import library.JavaInterface
import library.JavaMethodOwner

import library.KotlinClass
import library.KotlinInterface
import library.KotlinMethodOwner

//Extensions of Java classes

class JavaInheritor : <warning descr="Class 'library.JavaClass' must not be extended">JavaClass</warning>()

class JavaImplementor : <warning descr="Interface 'library.JavaInterface' must not be implemented">JavaInterface</warning>

interface JavaInterfaceInheritor : <warning descr="Interface 'library.JavaInterface' must not be extended">JavaInterface</warning>

class JavaMethodOverrider : JavaMethodOwner() {
  override fun <warning descr="Method 'doNotOverride()' must not be overridden">doNotOverride</warning>() = Unit
}

//Extensions of Kotlin classes

class KotlinInheritor : <warning descr="Class 'library.KotlinClass' must not be extended">KotlinClass</warning>()

class KotlinImplementor : <warning descr="Interface 'library.KotlinInterface' must not be implemented">KotlinInterface</warning>

interface KotlinInterfaceInheritor : <warning descr="Interface 'library.KotlinInterface' must not be extended">KotlinInterface</warning>

class KotlinMethodOverrider : KotlinMethodOwner() {
  override fun <warning descr="Method 'doNotOverride()' must not be overridden">doNotOverride</warning>() = Unit
}

fun anonymousClasses() {
  object : <warning descr="Class 'library.JavaClass' must not be extended">JavaClass</warning>() { }
  object : <warning descr="Interface 'library.JavaInterface' must not be implemented">JavaInterface</warning> { }
  object : <warning descr="Class 'library.KotlinClass' must not be extended">KotlinClass</warning>() { }
  object : <warning descr="Interface 'library.KotlinInterface' must not be implemented">KotlinInterface</warning> { }

  object : JavaMethodOwner() {
    override fun <warning descr="Method 'doNotOverride()' must not be overridden">doNotOverride</warning>() = Unit
  }

  object : KotlinMethodOwner() {
    override fun <warning descr="Method 'doNotOverride()' must not be overridden">doNotOverride</warning>() = Unit
  }
}