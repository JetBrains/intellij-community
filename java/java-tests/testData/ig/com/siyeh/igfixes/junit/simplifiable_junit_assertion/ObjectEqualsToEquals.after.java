import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Objects;

class ObjectEqualsToEquals {

  public void testObjectsEquals() {
      assertEquals(getFoo(), getBar());
  }

  String getFoo() { return "foo"; }
  String getBar() { return "foo"; }
}