import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

class Test {

  static class Base {
    public BigDecimal getTotal() {
      return null;
    }
  }

  public static void foo(List<? extends Base> list, List<Base> list1) {
    System.out.println(add(list, Base::getTotal));
    list1.forEach(Base::getTotal);
  }


  public static <T> BigDecimal add(Collection<T> objectsThatHaveBigDecimals, Function<T, ? extends BigDecimal> functionToGet) {
    return objectsThatHaveBigDecimals == null ? null : objectsThatHaveBigDecimals.stream().map(functionToGet).reduce(null, <error descr="Bad return type in method reference: cannot convert java.math.BigDecimal to ? extends java.math.BigDecimal">Test::add</error>);
  }

  public static BigDecimal add(BigDecimal... sequence) {
    return null;
  }
}