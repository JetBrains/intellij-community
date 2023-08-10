// "Fix all ''equals()' called on 'BigDecimal'' problems in file" "true"
import java.math.BigDecimal;
import java.util.Objects;

class BigDecimalEquals {
  public void foo(BigDecimal qux)
  {
    final BigDecimal foo = new BigDecimal(3);
    final BigDecimal bar = new BigDecimal(3);
    foo.equals(bar);
    if(foo.eq<caret>uals(bar))
    {
      return;
    }
    if(!Objects.equals(foo, bar)) {
      System.out.println("not equals");
    }
    if(Objects.equals(qux, bar)) {
      System.out.println("equals");
    }
  }

  boolean x(BigDecimal upper, BigDecimal lower) {
    return Objects.equals(upper, lower);
  }

  boolean test(int x, BigDecimal d1, BigDecimal d2) {
    return switch(x) {
      default -> d1.equals(d2);
    };
  }
}
