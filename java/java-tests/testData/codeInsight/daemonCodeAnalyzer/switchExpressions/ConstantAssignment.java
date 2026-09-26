public class ConstantAssignment {
  void dd(Double i) {
    switch (i) {
      case <error descr="Pattern expected for switch selector type 'java.lang.Double'">1.0</error> -> System.out.println(1);
      default -> throw new IllegalStateException("Unexpected value: " + i);
    }
  }

  void dd2(Long i) {
    switch (i) {
      case <error descr="Pattern expected for switch selector type 'java.lang.Long'">1L</error>:
        System.out.println(1);
      default:
        throw new IllegalStateException("Unexpected value: " + i);
    }
  }
}