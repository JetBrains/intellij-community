import java.util.Arrays;
import java.util.List;
import java.util.Objects;

class NullTest {

  enum StatusEnum {

    OK(1, "OK"), ERROR(2, "ERROR");

    final Integer code;

    final String msg;

    StatusEnum(Integer code, String msg) {
      this.code = code;
      this.msg = msg;
    }
  }

  public static StatusEnum getInstance(Integer code) {
    return Arrays.stream(StatusEnum.values())
      .filter(it -> Objects.equals(it.code, code))
      .findFirst()
      .orElse(null);
  }

  public void test2() {
    StatusEnum instance = getInstance(11);
    if (instance == null) {
      System.out.println("Instance not found");
      return;
    }
    System.out.println(instance);
  }

  static String getString(List<String> list) {
    return list.stream()
      .findFirst()
      .orElseGet(() -> null);
  }

  void testOrElseGet() {
    if (getString(List.of()) == null) {
      return;
    }
  }

  static void main() {
    new NullTest().test2();
  }

}