// "Rename unnamed variable|->Rename catch parameter of type 'NumberFormatException'" "true-preview"
import java.util.function.BiConsumer;

public class JavaTest {
  public static void main(String[] args) {
    BiConsumer<String, Integer> cons = (_, _) -> {
      for (int _ : new int[10]) {
        try {
          Integer.parseInt("123s");
        } catch (NumberFormatException numberFormatException) {
          System.out.println(numberFormatException);
        }
      }
    };
  }
}