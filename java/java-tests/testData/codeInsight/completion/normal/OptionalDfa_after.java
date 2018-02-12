import java.util.*;

class Foo {
    void test(Optional<Object> opt) {
        opt.filter(x -> x instanceof String)
          .map(s -> ((String) s).substring())
    }
}
