// "Replace lambda with method reference" "false"
import java.util.function.Consumer;

class Test {
  Consumer<Integer> c = (@Nonnull Integer i) -> System<caret>.out.println(i);
}