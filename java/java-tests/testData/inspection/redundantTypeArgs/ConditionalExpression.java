import java.util.*;
class Bug {
    void foo(Double d) {
       List<Class<?>> list = Arrays.<warning descr="Explicit type arguments can be inferred"><Class<?>></warning>asList(d == null ? Object.class : d.getClass());
    }
}