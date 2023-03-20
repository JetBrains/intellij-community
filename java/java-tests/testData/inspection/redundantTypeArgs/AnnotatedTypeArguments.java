import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

@Target(ElementType.TYPE_USE)
@interface Anno {}

class Test {
  void foo() {
    Supplier<List<Object>> supplier = ArrayList<@Anno Object>::new;
    Collections.<@Anno String>singleton("blah blah blah");
  }
}
