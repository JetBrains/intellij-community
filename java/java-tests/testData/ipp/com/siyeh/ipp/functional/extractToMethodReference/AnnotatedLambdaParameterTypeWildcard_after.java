import java.util.List;
import java.util.function.Consumer;
import java.lang.annotation.*;

class X {
    private static void accept(List<? extends @AAA String> i) {
        System.out.println(i);
    }

    @Target(ElementType.TYPE_USE)
    @interface AAA {}
  
    Consumer<Integer[]> c1 = X::accept;
}