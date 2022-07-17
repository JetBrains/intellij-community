import java.util.*;

class AdvancedArrayAccess {
  private static final int[] LENGTH = {0, 10, 20, 30};
  private static final int[] LENGTH2 = {0, 10, 20, 30};
  private static final String[] ARRAY = new String[] {"xyz".toLowerCase(Locale.ENGLISH)};

  void testInstanceOf() {
    Object[] arr = {new String("foo"), new Integer(0)};
    if(<warning descr="Condition 'arr[0] instanceof Number' is always 'false'">arr[0] instanceof Number</warning>) {
      System.out.println(((Number)arr[0]).intValue());
    }
  }

  void testStaticFinal() {
    if(<warning descr="Condition 'LENGTH[2] == 20' is always 'true'">LENGTH[2] == 20</warning>) {
      System.out.println("ok");
    }
  }

  void testAny(int i) {
    if(<warning descr="Condition 'LENGTH[i] > 30' is always 'false'">LENGTH[i] > 30</warning>) {
      System.out.println("Impossible");
    }
    if(LENGTH[i] > 20) {
      System.out.println("Possible");
    }
    if(<warning descr="Condition 'i < 3 && LENGTH[i] > 20' is always 'false'">i < 3 && <warning descr="Condition 'LENGTH[i] > 20' is always 'false' when reached">LENGTH[i] > 20</warning></warning>) {
      System.out.println("Impossible");
    }
  }

  void testCall(int i) {
    if(<warning descr="Condition 'ARRAY[i] == null' is always 'false'">ARRAY[i] == null</warning>) {
      System.out.println("Impossible");
    }
  }

  int[] getLengths() {
    // array is still read-only
    return LENGTH.clone();
  }

  int getLengthsCount() {
    // array is still read-only
    return LENGTH.length;
  }

  int[] getLength2() {
    // Reference leaks: do not consider an array as read-only
    return LENGTH2;
  }

  void testStaticFinal(int idx) {
    if(idx == 2 && <warning descr="Condition 'LENGTH[idx] == 20' is always 'true' when reached">LENGTH[idx] == 20</warning>) {
      System.out.println("ok");
    }
  }

  void testStaticFinalRW(int idx) {
    if(idx == 2 && LENGTH2[idx] == 20) {
      System.out.println("ok");
    }
  }

  void testWrite(int[] arr, int idx) {
    if(idx != 0) return;
    arr[idx] = 10;
    if(<warning descr="Condition 'arr[0] == 11' is always 'false'">arr[0] == 11</warning>) {
      System.out.println("impossible");
    }
  }

  void testSplitState(boolean b, String[] data) {
    data[1] = "A";
    data[2] = "B";
    int idx = b ? 0 : 1;
    data[idx] = "C";
    if(<warning descr="Condition 'data[2].equals(\"D\")' is always 'false'">data[2].equals("D")</warning>) {
      System.out.println("never");
    }
    if(<warning descr="Condition 'data[1].equals(\"E\")' is always 'false'">data[1].equals("E")</warning>) {
      System.out.println("never");
    }
    if(data[1].equals("A") && <warning descr="Condition 'b' is always 'true' when reached">b</warning>) {
      System.out.println("Only if b is true");
    }
  }

  // Should not be too complex
  static int max(float[] array) {
    int max = 0;
    float val = array[0];
    for (int i = 1; i < array.length; i++) {
      if (val < array[i]) {
        max = i;
        val = array[i];
      }
    }
    return max;
  }

  void processArray(String[] arr) {
    for(int i=0; i<arr.length; i++) {
      if(arr[i].isEmpty()) arr[i] = null; // No NPE warning here even if we update arr[i] to null we'll never visit same element twice
    }
  }

  void testMethodQualifier() {
    if(getData()[0] == null) {
      System.out.println(getData()[0].<warning descr="Method invocation 'trim' will produce 'NullPointerException'">trim</warning>());
    }
  }

  final String[] getData() {
    return new String[] {"a", "b", "c"};
  }

  void testConditional(String[] arr, int idx) {
    if(idx == 0) {
      arr[idx] = "foo";
    }
    if(<warning descr="Condition 'arr[0].equals(\"bar\") && idx == 0' is always 'false'">arr[0].equals("bar") && <warning descr="Condition 'idx == 0' is always 'false' when reached">idx == 0</warning></warning>) {
      System.out.println("never");
    }
  }

  void testLocalRewritten() {
    String[] arr = {"foo", "bar", "baz"};
    arr[0] = "qux";
    int result = 0;
    if(<warning descr="Condition 'arr[1].equals(\"bar\")' is always 'true'">arr[1].equals("bar")</warning>) {
      result = 1;
    }
    if(<warning descr="Condition 'arr[2].equals(\"bar\")' is always 'false'">arr[2].equals("bar")</warning>) {
      result = 1;
    }
    if(<warning descr="Condition 'arr[0].equals(\"foo\")' is always 'false'">arr[0].equals("foo")</warning>) {
      result = 2;
    }
    System.out.println(result);
    arr = new String[] {"bar", "baz", "foo"};
    arr[2] = "qux";
    if(<warning descr="Condition 'arr[1].equals(\"bar\")' is always 'false'">arr[1].equals("bar")</warning>) {
      result = 2;
    }
    if(<warning descr="Condition 'arr[2].equals(\"qux\")' is always 'true'">arr[2].equals("qux")</warning>) {
      <warning descr="Variable is already assigned to this value">result</warning> = 1;
    }
    if(<warning descr="Condition 'arr[0].equals(\"bar\")' is always 'true'">arr[0].equals("bar")</warning>) {
      result = 2;
    }
    System.out.println(result);
  }

  void testObjectVar() {
    Object x;
    x = new String[] {"foo", "bar"};
    if(((String[])x)[0].equals("foo")) {
      System.out.println("Not supported if variable type is not array");
    }
  }

  void testWidening(byte[] b, int idx) {
    int i = b[idx] & 0xFF;
    if(<warning descr="Condition 'i > 255' is always 'false'">i > 255</warning>) {
      System.out.println("Impossible");
    }
  }

  void testNonInitializedShort() {
    int[] x = new int[2];
    if(<warning descr="Condition 'x[0] == 0' is always 'true'">x[0] == 0</warning>) {
      System.out.println("Always");
    }
  }

  void testFor2() {
    String[] x = new String[2];
    for (int i = 0; i < 2; i++) {
      x[i] = String.valueOf(i);
    }
    for (int i = 0; i < 2; i++) {
      System.out.println(x[i].trim());
    }
  }

  void testFor3() {
    int[] x = new int[3];
    for (int i = 0; i < 3; i++) {
      x[i] = i;
    }
    if(<warning descr="Condition 'x[2] == 2' is always 'true'">x[2] == 2</warning>) {
      System.out.println("Always");
    }
  }

  int[] getArray() {
    return new int[0];
  }

  void testInitializerWrong() {
    getArray() = <error descr="Array initializer is not allowed here">{1,2,3}</error>;
  }

  void testLongInitializer() {
    int[] arr = {0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32,33,34,35,36,37,38,39,40};
    arr[0] = 1;
    if(<warning descr="Condition 'arr[20] == 20' is always 'true'">arr[20] == 20</warning>) {}
    if(<warning descr="Condition 'arr[31] == 31' is always 'true'">arr[31] == 31</warning>) {}
    // Too long initializers aren't tracked for mutable arrays
    if(arr[32] == 32) {}
    if(arr[33] == 33) {}
    int[] arr2 = {0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32,33,34,35,36,37,38,39,40};
    if(<warning descr="Condition 'arr2[20] == 20' is always 'true'">arr2[20] == 20</warning>) {}
    if(<warning descr="Condition 'arr2[31] == 31' is always 'true'">arr2[31] == 31</warning>) {}
    // ..but tracked for immutable arrays
    if(<warning descr="Condition 'arr2[32] == 32' is always 'true'">arr2[32] == 32</warning>) {}
    if(<warning descr="Condition 'arr2[33] == 33' is always 'true'">arr2[33] == 33</warning>) {}
  }

  private static void change(Object[] x) {
    ((String[]) x[0])[0] = "OK";
  }

  // IDEA-210027
  public static void main(String[] args) {
    String[] array = new String[] { null };
    change(new Object[] { array });
    System.out.println(array[0]); // <-- Value 'array[0]' was reported as always 'null'
  }

  private static final int[] ARR_FIRST = {<error descr="Illegal forward reference">ARR_SECOND</error>[0], 1};
  private static final int[] ARR_SECOND = {ARR_FIRST[0], 1};

  void testSelfReference() {
    int[] arr = {<error descr="Variable 'arr' might not have been initialized">arr</error>[0], 1};

    if (arr[0] == 0) {}
    if (<warning descr="Condition 'arr[1] == 1' is always 'true'">arr[1] == 1</warning>) {}

    if (ARR_FIRST[0] == 0) {}
    if (<warning descr="Condition 'ARR_FIRST[1] == 1' is always 'true'">ARR_FIRST[1] == 1</warning>) {}
  }

}
