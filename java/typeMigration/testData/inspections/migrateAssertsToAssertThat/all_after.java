import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import java.util.Collection;

import static org.hamcrest.CoreMatchers.*;

public class TestCase {
  void m() {
    Assert.assertThat(2, not(is(3)));
    Assert.assertThat(2, is(3));
    Assert.assertThat(2, org.hamcrest.number.OrderingComparison.greaterThan(3));
    Assert.assertThat(2, org.hamcrest.number.OrderingComparison.lessThan(3));
    Assert.assertThat(2, org.hamcrest.number.OrderingComparison.greaterThanOrEqualTo(3));
    Assert.assertThat(2, org.hamcrest.number.OrderingComparison.lessThanOrEqualTo(3));

    Assert.assertThat(2 != 3, not(is(false)));
    Assert.assertThat(2 == 3, not(is(false)));
    Assert.assertThat(2, org.hamcrest.number.OrderingComparison.lessThanOrEqualTo(3));
    Assert.assertThat(2, org.hamcrest.number.OrderingComparison.greaterThanOrEqualTo(3));
    Assert.assertThat(2, org.hamcrest.number.OrderingComparison.lessThan(3));
    Assert.assertThat(2, org.hamcrest.number.OrderingComparison.greaterThan(3));
  }

  void m2() {
    Assert.assertThat("asd", is("zxc"));
    Assert.assertThat("asd", sameInstance("zxc"));
    Assert.assertThat("asd", containsString("qwe"));
  }

  void m3(Collection c, Object o) {
    Assert.assertThat(o, anyOf(c));
    Assert.assertThat(c, is(o));
    Assert.assertThat("msg", c, is(o));
    Assert.assertThat(c, notNullValue());
    Assert.assertThat(c, nullValue());
  }

  void m(int[] a, int[] b) {
    Assert.assertThat(a, is(b));
  }
}