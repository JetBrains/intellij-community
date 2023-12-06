import java.util.function.Consumer;
import java.lang.annotation.*;

class X {
    private static void accept(@AAA Integer[] i) {
        System.out.println(i);
    }

    @Target(ElementType.PARAMETER)
    @interface AAA {}
  
    Consumer<Integer[]> c1 = X::<caret>accept;
}