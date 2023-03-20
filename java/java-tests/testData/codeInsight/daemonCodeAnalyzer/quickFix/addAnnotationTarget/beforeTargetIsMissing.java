// "Make annotation applicable to type uses" "true-preview"
import java.util.List;

@interface Foo {}

class Main {
  List<@Foo<caret> Integer> answers = List.of(42);
}