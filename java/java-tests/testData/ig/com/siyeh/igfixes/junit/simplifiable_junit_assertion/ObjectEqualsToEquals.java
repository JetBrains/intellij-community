import static org.junit.Assert.assertTrue;

import java.util.Objects;

class ObjectEqualsToEquals {

  public void testObjectsEquals() {
      <warning descr="'assertTrue()' can be simplified to 'assertEquals()'"><caret>assertTrue</warning>(Objects.equals(getFoo(), getBar()));
  }

  String getFoo() { return "foo"; }
  String getBar() { return "foo"; }
}