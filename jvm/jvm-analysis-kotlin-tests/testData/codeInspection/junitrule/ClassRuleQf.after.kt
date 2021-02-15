package test

import org.junit.rules.TestRule
import org.junit.ClassRule
import test.SomeTestRule

object PrivateClassRule {
  @kotlin.jvm.JvmField
  @ClassRule
  var x = SomeTestRule()
}