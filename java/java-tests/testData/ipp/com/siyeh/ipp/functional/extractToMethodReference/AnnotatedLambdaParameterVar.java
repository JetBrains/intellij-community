import java.util.function.Consumer;
import java.lang.annotation.*;

class X {
    @Target(ElementType.PARAMETER)
    @interface AAA {}
  
    Consumer<Integer[]> c1 = (@AAA var<caret> i) -> System.out.println(i);
}