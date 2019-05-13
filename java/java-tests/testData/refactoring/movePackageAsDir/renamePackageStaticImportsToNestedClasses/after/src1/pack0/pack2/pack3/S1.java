package pack0.pack2.pack3;
import static pack0.pack2.pack3.S1.TestEnum.*;

public class S1 {

  public void test() {
    System.out.println(TEST_STRING_1);
  }

  public enum TestEnum {
    TEST_STRING_1();
  }
}