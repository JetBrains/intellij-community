// "Inline 'map()' body into the next 'map()' call" "false"
import java.util.List;
import java.util.stream.IntStream;

public class Main {
  public static void test(List<Foo> list) {
    list.stream().<caret>map(Foo::getBar).map(bar -> bar.getID());
  }
}