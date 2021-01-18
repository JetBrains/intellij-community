package test;

import org.junit.rules.TestRule;
import org.junit.ClassRule;
import test.SomeTestRule;

class ClassRuleTest {
  @ClassRule
  static SomeTestRule x = new SomeTestRule();

  @ClassRule
  public SomeTestRule y = new SomeTestRule();

  @ClassRule
  private SomeTestRule z = new SomeTestRule();
}