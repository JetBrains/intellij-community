import java.util.function.Consumer;
import java.lang.annotation.*;

class X {
    @Target({ElementType.TYPE_USE, ElementType.PARAMETER})
    @interface AAA {}
  
    Consumer<Integer[]> c1 = (@AAA Integer<caret> @AAA [] i) -> System.out.println(i);
}