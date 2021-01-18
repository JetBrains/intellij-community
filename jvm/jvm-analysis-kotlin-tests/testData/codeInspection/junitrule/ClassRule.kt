package test

import org.junit.rules.TestRule
import org.junit.ClassRule
import test.SomeTestRule

object PrivateClassRule {
  @ClassRule
  private var <error descr="Fields annotated with '@org.junit.ClassRule' should be 'public'">x</error> = SomeTestRule()

  @ClassRule
  private var <error descr="Field type should be subtype of 'org.junit.rules.TestRule'"><error descr="Fields annotated with '@org.junit.ClassRule' should be 'public'">y</error></error> = 0
}