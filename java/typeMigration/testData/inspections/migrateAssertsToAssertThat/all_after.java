import org.hamcrest.Matchers;
import org.hamcrest.number.OrderingComparison;
import org.junit.Assert;
import java.util.Collection;

import static org.hamcrest.Matchers.*;
import static org.hamcrest.number.OrderingComparison.*;

public class TestCase {
  void m() {
    Assert.assertThat(2, not(is(3)));
    Assert.assertThat(2, is(3));
    Assert.assertThat(2, OrderingComparison.greaterThan(3));
    Assert.assertThat(2, OrderingComparison.lessThan(3));
    Assert.assertThat(2, OrderingComparison.greaterThanOrEqualTo(3));
    Assert.assertThat(2, OrderingComparison.lessThanOrEqualTo(3));

    Assert.assertThat(2 != 3, is(false));
    Assert.assertThat(2 == 3, is(false));
    Assert.assertThat(2, OrderingComparison.lessThanOrEqualTo(3));
    Assert.assertThat(2, OrderingComparison.greaterThanOrEqualTo(3));
    Assert.assertThat(2, OrderingComparison.lessThan(3));
    Assert.assertThat(2, OrderingComparison.greaterThan(3));
  }

  void m2() {
    Assert.assertThat("asd", is("zxc"));
    Assert.assertThat("asd", sameInstance("zxc"));
    Assert.assertThat("asd", containsString("qwe"));
  }

  void m3(Collection<String> c, String o) {
    Assert.assertThat(c, hasItem(o));
    Assert.assertThat(o, is(c));
    Assert.assertThat("msg", o, is(c));
    Assert.assertThat(c, notNullValue());
    Assert.assertThat(c, nullValue());
    Assert.assertThat(c, not(hasItem(o)));
  }

  void m(int[] a, int[] b) {
    Assert.assertThat(b, is(a));
  }
}