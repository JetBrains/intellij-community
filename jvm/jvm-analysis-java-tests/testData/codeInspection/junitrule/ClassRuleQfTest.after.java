package test;

import org.junit.rules.TestRule;
import org.junit.ClassRule;
import test.SomeTestRule;

class ClassRuleTest {
  @ClassRule
  public static SomeTestRule x = new SomeTestRule();

  @ClassRule
  public static SomeTestRule y = new SomeTestRule();

  @ClassRule
  public static SomeTestRule z = new SomeTestRule();
}