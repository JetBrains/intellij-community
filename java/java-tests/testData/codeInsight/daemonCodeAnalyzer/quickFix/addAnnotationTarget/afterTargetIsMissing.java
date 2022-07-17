// "Make annotation applicable to type uses" "true"
import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import java.util.List;

@Target(ElementType.TYPE_USE)
@interface Foo {}

class Main {
  List<@Foo Integer> answers = List.of(42);
}