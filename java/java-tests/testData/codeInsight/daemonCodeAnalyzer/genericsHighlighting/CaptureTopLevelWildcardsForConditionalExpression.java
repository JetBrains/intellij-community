import java.util.*;
class Bug {
    void foo(Double d) {
       List<Class<?>> list = Arrays.<error descr="Incompatible types. Found: 'java.util.List<java.lang.Class<capture<? extends java.lang.Object>>>', required: 'java.util.List<java.lang.Class<?>>'">asList</error>(d == null ? Object.class : d.getClass());
    }
}