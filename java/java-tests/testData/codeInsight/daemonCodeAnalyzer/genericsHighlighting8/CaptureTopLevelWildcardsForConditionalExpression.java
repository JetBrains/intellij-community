import java.util.*;
class Bug {
    void foo(Double d) {
       List<Class<?>> list = Arrays.asList(d == null ? Object.class : d.getClass());
    }
}
