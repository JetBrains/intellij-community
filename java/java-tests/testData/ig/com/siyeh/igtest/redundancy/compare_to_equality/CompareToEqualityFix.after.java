import java.math.BigDecimal;

public class CompareToEqualityFix {

  boolean isSame(String name) {
    return name.equals("value");
  }
  
  boolean isNotSame(String name) {
    return !name.equals("Ezekiel");
  }

  boolean isMinusOne(Integer i) {
    return i.equals(-1);
  }
  
  boolean mathIsHardLetsGoShopping(BigDecimal bd) {
    return bd.compareTo(BigDecimal.TEN) == 0;
  }

  boolean isTwo(Count count) {
    return count == Count.TWO;
  }
  
  boolean isNotThree(Count count) {
    return count != Count.THREE;
  }

  void m() {
    // test cases taken from the old "Convert 'compareTo()' expression to 'equals()' call" intention
    Integer i1 = new Integer(0);
    Integer i2 = new Integer(2);

    boolean b1 = i1.equals(i2);
    boolean b2 = !"asd".substring(1).equals("qwe".substring(2));
    boolean b3 = "asd".compareTo("qwe") == 1;
    boolean b4 = i1.equals(i2);
  }
}
enum Count {
  ONE, TWO, THREE;

  class Sub {
    void test(Count other) {
      if (Count.this == other) {
        System.out.println("equal");
      }
    }
  }
}
