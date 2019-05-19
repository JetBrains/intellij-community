import java.util.*;

class Foo {
    final class X {}
    class Y extends X {}
    class YY extends X {}
    interface Z {};

    void test(Object o) {
        if((o instanceof Y && o instanceof Z) || (o instanceof YY && o instanceof Z)) {
            o.hashC<caret>
        }
    }
}
