import java.util.*;

class EqualsWithItself_ignoreNonFinalClassesInTest {

  void objectTest(Object o) {
    org.junit.jupiter.api.Assertions.assertEquals(o, o);
  }

  void stringTest(String s) {
    org.junit.jupiter.api.Assertions.<warning descr="'assertEquals()' called on itself">assertEquals</warning>(s, s);
  }

  boolean stringEquals(String s) {
    return s.<warning descr="'equalsIgnoreCase()' called on itself">equalsIgnoreCase</warning>(s);
  }

  void arrayTest(){
    org.junit.jupiter.api.Assertions.<warning descr="'assertArrayEquals()' called on itself">assertArrayEquals</warning>(new int[]{1,2}, new int[]{1,2});
  }

  void primitiveTest(int i) {
    org.junit.jupiter.api.Assertions.<warning descr="'assertEquals()' called on itself">assertEquals</warning>(i, i);
  }

  void customClassTest(FinalClass finalClass) {
    org.junit.jupiter.api.Assertions.assertEquals(finalClass, finalClass);
  }

  private static final class FinalClass{
  }

  void testComparator() {
    if (myComparator.<warning descr="'compare()' called on itself">compare</warning>(null, null) == 0) {
      System.out.println("ok");
    }
    org.testng.Assert.assertEquals(myComparator.compare(null, null), 0);
  }

  Comparator myComparator = new Comparator() {
    @Override
    public int compare(Object o1, Object o2) {
      return 0;
    }
  };
}