package com.intellij.configurationStore

import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

class ResourceLoader(id: String) : ReadOnlyProperty<MyUI, String> {
  operator fun provideDelegate(thisRef: MyUI, prop: KProperty<*>): ReadOnlyProperty<MyUI, String> {
    checkProperty(thisRef, prop.name)
    return this
  }

  override fun getValue(thisRef: MyUI, property: KProperty<*>): String {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  private fun checkProperty(thisRef: MyUI, name: String) {}
}

fun bindResource(id: String): ResourceLoader {
  return ResourceLoader(id)
}

class MyUI {
  val image by bindResource("f")
  val text by bindResource("d")
}

fun main(a: Array<String>) {
  MyUI()
}