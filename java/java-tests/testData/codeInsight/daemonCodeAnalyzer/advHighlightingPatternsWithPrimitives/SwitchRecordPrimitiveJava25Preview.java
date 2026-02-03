package test.something;

public class SwitchRecordPrimitiveJava25Preview {
  static void main(String[] args) {
    Integer a = 1;
    switch (a) {
      case int _ -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }
  }
}
