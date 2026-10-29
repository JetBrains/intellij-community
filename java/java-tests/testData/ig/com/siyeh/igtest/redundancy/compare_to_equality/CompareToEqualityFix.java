import java.math.BigDecimal;

public class CompareToEqualityFix {

  boolean isSame(String name) {
    return name.<warning descr="'compareTo()' used for equality check"><caret>compareTo</warning>("value") == 0;
  }
  
  boolean isNotSame(String name) {
    return name.<warning descr="'compareTo()' used for equality check">compareTo</warning>("Ezekiel") != 0;
  }

  boolean isMinusOne(Integer i) {
    return i.<warning descr="'compareTo()' used for equality check">compareTo</warning>(-1) == 0;
  }
  
  boolean mathIsHardLetsGoShopping(BigDecimal bd) {
    return bd.compareTo(BigDecimal.TEN) == 0;
  }

  boolean isTwo(Count count) {
    return count.<warning descr="'compareTo()' used for equality check">compareTo</warning>(Count.TWO) == 0;
  }
  
  boolean isNotThree(Count count) {
    return count.<warning descr="'compareTo()' used for equality check">compareTo</warning>(Count.THREE) != 0;
  }
}
enum Count {
  ONE, TWO, THREE
}
