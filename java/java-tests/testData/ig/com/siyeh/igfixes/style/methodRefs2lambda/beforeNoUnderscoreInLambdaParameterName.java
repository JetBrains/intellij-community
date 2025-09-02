// "Replace method reference with lambda" "true-preview"
import java.util.function.IntConsumer;

public class Main {
  void test(int _) {};

  IntConsumer c = this::te<caret>st;
}