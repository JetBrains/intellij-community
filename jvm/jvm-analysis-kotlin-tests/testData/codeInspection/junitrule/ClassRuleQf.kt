package test

import org.junit.rules.TestRule
import org.junit.ClassRule
import test.SomeTestRule

object PrivateClassRule {
  @ClassRule
  private var x = SomeTestRule()
}