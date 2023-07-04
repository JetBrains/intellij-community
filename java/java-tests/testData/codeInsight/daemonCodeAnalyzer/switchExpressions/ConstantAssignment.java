public class ConstantAssignment {
  void dd(Double i) {
    switch (i) {
      case <error descr="Unexpected type. Found: 'double', required: 'char, byte, short, int, Character, Byte, Short, Integer, String'">1.0</error> -> System.out.println(1);
      default -> throw new IllegalStateException("Unexpected value: " + i);
    }
  }

  void dd2(Long i) {
    switch (i) {
      case <error descr="Unexpected type. Found: 'long', required: 'char, byte, short, int, Character, Byte, Short, Integer, String'">1L</error>:
        System.out.println(1);
      default:
        throw new IllegalStateException("Unexpected value: " + i);
    }
  }
}