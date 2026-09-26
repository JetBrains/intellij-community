// "Fix all ''equals()' called on 'BigDecimal'' problems in file" "true"
import java.math.BigDecimal;
import java.util.Objects;

class BigDecimalEquals {
  public void foo(BigDecimal qux)
  {
    final BigDecimal foo = new BigDecimal(3);
    final BigDecimal bar = new BigDecimal(3);
    foo.equals(bar);
    if(foo.compareTo(bar) == 0)
    {
      return;
    }
    if(!(foo.compareTo(bar) == 0)) {
      System.out.println("not equals");
    }
    if(qux != null && qux.compareTo(bar) == 0) {
      System.out.println("equals");
    }
  }

  boolean x(BigDecimal upper, BigDecimal lower) {
    return upper == null ? lower == null : upper.compareTo(lower) == 0;
  }

  boolean test(int x, BigDecimal d1, BigDecimal d2) {
    return switch(x) {
      default -> d1.compareTo(d2) == 0;
    };
  }
}
