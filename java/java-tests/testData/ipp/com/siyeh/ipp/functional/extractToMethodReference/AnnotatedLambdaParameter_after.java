import java.util.function.Consumer;

class X {
    private static void accept(@AAA Integer i) {
        System.out.println(i);
    }

    @interface AAA {}
  
    Consumer<Integer> c1 = X::<caret>accept;
}