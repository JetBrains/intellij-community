package dfa;

public class SwitchWithPrimitive {
  public static void main(String[] args) {
    testTestPattern(2L);
    System.out.println(withErasureSwitch(1));
    System.out.println(exhaustiveByPrimitives(new RecordCharObj('1')));
  }

  public static int exhaustiveByPrimitives(RecordCharObj source) {
    switch (source) {
      case RecordCharObj(char source1) -> System.out.println("1");
      case <warning descr="Switch label 'RecordCharObj(int source1)' is unreachable">RecordCharObj(int source1)</warning> -> System.out.println("1");
    }
    return 1;
  }

  record RecordCharObj(Character x) {
  }

  public static <T extends Integer> boolean withErasureSwitch(T i) {
    return <warning descr="Condition 'switch (i) { case long a -> false; }' is always 'false'">switch (i) {
      case <warning descr="Switch label 'long a' is the only reachable in the whole switch">long a</warning> -> false;
    }</warning>;
  }

  private static void testTestPattern(Long l2) {
    long l = 0;
    switch (l) {
      case <warning descr="Switch label 'int i' is the only reachable in the whole switch">int i</warning> -> System.out.println("1");
      case long i -> System.out.println("2");
    }
    switch (l) {
      case <warning descr="Switch label '0L' is the only reachable in the whole switch">0L</warning> -> System.out.println("2");
      case 1L -> System.out.println("2");
      case 2L -> System.out.println("2");
      case int i -> System.out.println("1");
      case long i -> System.out.println("1");
    }
    switch (l) {
      case 1L -> System.out.println("2");
      case 2L -> System.out.println("2");
      case <warning descr="Switch label 'long i' is the only reachable in the whole switch">long i</warning> -> System.out.println("1");
    }
    switch (l2) {
      case 0L -> System.out.println("2");
      case 1L -> System.out.println("2");
      case 2L -> System.out.println("2");
      case long i -> System.out.println("1");
    }
    switch (l2) {
      case 1L -> System.out.println("2");
      case 2L -> System.out.println("2");
      case long i -> System.out.println("1");
    }
  }
}
