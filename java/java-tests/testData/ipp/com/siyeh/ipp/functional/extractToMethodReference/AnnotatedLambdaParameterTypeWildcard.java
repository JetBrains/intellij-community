import java.util.List;
import java.util.function.Consumer;
import java.lang.annotation.*;

class X {
    @Target(ElementType.TYPE_USE)
    @interface AAA {}
  
    Consumer<Integer[]> c1 = (List<? <caret>extends @AAA String> i) -> System.out.println(i);
}