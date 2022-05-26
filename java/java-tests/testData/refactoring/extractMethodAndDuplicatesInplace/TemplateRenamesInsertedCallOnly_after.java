import org.jetbrains.annotations.NotNull;

import static java.lang.Integer.getInteger;

public abstract class Test {

  private void test() {
    String integer = renamed();
    int x = getInteger("0");
    System.out.println(integer);
  }

    @NotNull
    private String renamed() {
        return "4" + "2";
    }
}