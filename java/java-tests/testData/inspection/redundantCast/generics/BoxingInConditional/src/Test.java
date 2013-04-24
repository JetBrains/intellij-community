//This is a test for JDK_15 LanguageLevel
public class Test {
  private Integer foo(String s, Integer i) {
    return s == null ? i : (Integer)2;
  }

  private int foo1(String s, Integer i) {
    return s == null ? i : (Integer)2;
  }
}