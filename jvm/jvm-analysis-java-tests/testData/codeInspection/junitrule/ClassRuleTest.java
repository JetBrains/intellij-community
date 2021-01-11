package test;

import org.junit.rules.TestRule;
import org.junit.ClassRule;
import test.SomeTestRule;

class ClassRuleTest {
  @ClassRule static SomeTestRule <error descr="Fields annotated with '@org.junit.ClassRule' should be 'public'">x</error> = new SomeTestRule();

  @ClassRule public SomeTestRule <error descr="Fields annotated with '@org.junit.ClassRule' should be 'static'">y</error> = new SomeTestRule();

  @ClassRule private SomeTestRule <error descr="Fields annotated with '@org.junit.ClassRule' should be 'public' and 'static'">z</error> = new SomeTestRule();
}