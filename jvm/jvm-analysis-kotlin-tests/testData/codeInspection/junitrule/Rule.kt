package test

import org.junit.Rule

class PrivateRule {
  @Rule
  private var <error descr="Fields annotated with '@org.junit.Rule' should be 'public'">x</error> = 0
}