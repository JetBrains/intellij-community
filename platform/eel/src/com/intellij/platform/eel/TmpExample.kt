// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel

fun foobar1(@GeneratedBuilder opts: FoobarOpts) {
  println("foobar1")
  println(opts.foo)
  println(opts.bar)
}

fun Any.foobar2(@GeneratedBuilder opts: FoobarOpts) {
  println("foobar2")
  println(this)
  println(opts.foo)
  println(opts.bar)
}

fun Any.foobar3(@GeneratedBuilder opts: FoobarOpts) {
  println("foobar3")
  println(this)
  println(opts.foo)
  println(opts.bar)
}

fun Map<String, String>.foobar4(@GeneratedBuilder opts: FoobarOpts) {
  println("foobar4")
  println(this)
  println(opts.foo)
  println(opts.bar)
}

class OurThing

fun OurThing.foobar5(@GeneratedBuilder opts: FoobarOpts) {
  println("foobar5")
  println(this)
  println(opts.foo)
  println(opts.bar)
}

fun OurThingHelpers.Foobar5.hurrDurr() = apply {
  foo("hurr durr")
  bar(31337)
}

interface FoobarOpts {
  val foo: String? get() = null
  val bar: Int? get() = null
}

suspend fun main() {
  foobar1().foo("123").bar(456).eelIt()
  (3.14159).foobar2().foo("xxx").eelIt()
  (3.14159).foobar3().foo("yyy").eelIt()
  mapOf("a" to "b").foobar4().foo("zzz").eelIt()
  OurThing().foobar5().hurrDurr().eelIt()
}