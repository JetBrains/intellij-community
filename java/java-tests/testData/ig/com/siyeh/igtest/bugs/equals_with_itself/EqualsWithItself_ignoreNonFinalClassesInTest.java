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
}