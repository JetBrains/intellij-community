package test;

import org.junit.Rule;
import org.junit.rules.TestRule;

public class RuleTest {
  @Rule
  private int <error descr="Fields annotated with '@org.junit.Rule' should be 'public'">x</error>;

  @Rule
  public static int <error descr="Fields annotated with '@org.junit.Rule' should be non-static">y</error>;
}