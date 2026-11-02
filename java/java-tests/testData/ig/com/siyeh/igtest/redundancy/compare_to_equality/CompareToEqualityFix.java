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

  void m() {
    // test cases taken from the old "Convert 'compareTo()' expression to 'equals()' call" intention
    Integer i1 = new Integer(0);
    Integer i2 = new Integer(2);

    boolean b1 = i1.<warning descr="'compareTo()' used for equality check">compareTo</warning>(i2) == 0;
    boolean b2 = "asd".substring(1).<warning descr="'compareTo()' used for equality check">compareTo</warning>("qwe".substring(2)) != 0;
    boolean b3 = "asd".compareTo("qwe") == 1;
    boolean b4 = (i1.<warning descr="'compareTo()' used for equality check">compareTo</warning>(i2)) == (0);
  }
}
enum Count {
  ONE, TWO, THREE;

  class Sub {
    void test(Count other) {
      if (<warning descr="'compareTo()' used for equality check">compareTo</warning>(other) == 0) {
        System.out.println("equal");
      }
    }
  }
}
