// "Rename unnamed variable|->Rename lambda parameter of type 'String'" "true-preview"
import java.util.function.BiConsumer;

public class JavaTest {
  public static void main(String[] args) {
    BiConsumer<String, Integer> cons = (_, _) -> {
      for (int _ : new int[10]) {
        try {
          Integer.parseInt("123s");
        } catch (NumberFormatException _) {
          System.out.println(<caret>_);
        }
      }
    };
  }
}