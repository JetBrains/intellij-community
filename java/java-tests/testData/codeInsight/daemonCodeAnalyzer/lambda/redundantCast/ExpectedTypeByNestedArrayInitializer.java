
import java.util.function.IntFunction;

class Test {

  public static void main(String[] args) {
        Object[][] o = new Object[][] {{
                (IntFunction<String>) integer -> Integer.toString(integer)
        }};

        Object[] o1 = new Object[] {
                (IntFunction<String>)  integer -> Integer.toString(integer)
        };
  }
}