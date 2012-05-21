// "Move initializer to setUp method" "true"
package junit.framework;

public class X extends TestCase {
  <caret>int i = 7;
}

//HACK: making test possible without attaching jUnit
public abstract class TestCase {
}
