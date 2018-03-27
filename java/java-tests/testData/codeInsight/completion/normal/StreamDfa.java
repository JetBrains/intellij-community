import java.util.*;

class Foo {
    void test(List<?> obj) {
        obj.stream()
          .filter(x -> x instanceof String)
          .forEach(e -> e.subst<caret>);
    }
}
