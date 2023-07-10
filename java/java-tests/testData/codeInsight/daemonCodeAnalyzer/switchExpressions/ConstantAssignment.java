public class ConstantAssignment {
  void dd(Double i) {
    switch (i) {
      case <error descr="Incompatible types. Type of constant label: 'double', switch selector type: 'java.lang.Double'">1.0</error> -> System.out.println(1);
      default -> throw new IllegalStateException("Unexpected value: " + i);
    }
  }

  void dd2(Long i) {
    switch (i) {
      case <error descr="Incompatible types. Type of constant label: 'long', switch selector type: 'java.lang.Long'">1L</error>:
        System.out.println(1);
      default:
        throw new IllegalStateException("Unexpected value: " + i);
    }
  }
}