import java.util.*;

class Testcase {

  void intToLong() {
    Integer i = 2;
    Long l = 2L;
    System.out.println(<warning descr="Result of 'l.equals((long) i)' is always 'true'">l.equals((long) i)</warning>);
  }

  private static final Long TEST = new Long(1234);

  public static void main(String[] args) {
    final Map<String, Long> hashMap = new HashMap<>();
    hashMap.put("Hello", TEST);

    Long l = hashMap.get("Hello");
    if(l != null) {
      if(l == TEST) {
        System.out.println("Out");
      }
    }
  }

  void test() {
    <error descr="Incompatible types. Found: 'null', required: 'int'">int x = null;</error>
    Integer boxed = x;
    if (<warning descr="Condition 'boxed == 5' is always 'false'">boxed == 5</warning>) {}
  }
}