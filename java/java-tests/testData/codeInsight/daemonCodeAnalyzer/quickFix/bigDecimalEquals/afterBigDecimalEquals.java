// "Fix all ''equals()' called on 'java.math.BigDecimal'' problems in file" "true"
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
}
