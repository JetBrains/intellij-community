import java.util.*;

class Testcase {
  // IDEA-219122
  public static void boxWithCast() {
    Double d = 1D;
    Long l = 1L;
    if (<warning descr="Condition 'd.equals(Double.valueOf(l))' is always 'true'">d.equals(Double.valueOf(l))</warning>) {
      System.out.println("e. d: " + d + " l: " + l);
    } else {
      System.out.println("ne. d: " + d + " l: " + l);
    }
  }

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