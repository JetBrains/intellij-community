import java.util.*;
class Bug {
    void foo(Double d) {
       List<Class<?>> list = Arrays.<Class<?>>asList(d == null ? Object.class : d.getClass());
    }
}