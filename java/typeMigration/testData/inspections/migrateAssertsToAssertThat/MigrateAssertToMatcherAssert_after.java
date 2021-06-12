import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.hamcrest.number.OrderingComparison;
import org.junit.Assert;
import java.util.Collection;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.number.OrderingComparison.*;

public class MigrateAssertToMatcherAssert {
  void m() {
    assertThat(2, not(is(3)));
    assertThat(2, is(3));
    assertThat(2, OrderingComparison.greaterThan(3));
    assertThat(2, OrderingComparison.lessThan(3));
    assertThat(2, OrderingComparison.greaterThanOrEqualTo(3));
    assertThat(2, OrderingComparison.lessThanOrEqualTo(3));

    assertThat(2 != 3, is(false));
    assertThat(2 == 3, is(false));
    assertThat(2, OrderingComparison.lessThanOrEqualTo(3));
    assertThat(2, OrderingComparison.greaterThanOrEqualTo(3));
    assertThat(2, OrderingComparison.lessThan(3));
    assertThat(2, OrderingComparison.greaterThan(3));
  }

  void m2() {
    assertThat("asd", is("zxc"));
    assertThat("asd", sameInstance("zxc"));
    assertThat("asd", containsString("qwe"));
  }

  void m3(Collection<String> c, String o) {
    assertThat(c, hasItem(o));
    assertThat(o, is(c));
    assertThat("msg", o, is(c));
    assertThat(c, notNullValue());
    assertThat(c, nullValue());
    assertThat(c, not(hasItem(o)));
  }

  void m(int[] a, int[] b) {
    assertThat(b, is(a));
  }
}