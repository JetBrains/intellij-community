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
}
enum Count {
  ONE, TWO, THREE
}
