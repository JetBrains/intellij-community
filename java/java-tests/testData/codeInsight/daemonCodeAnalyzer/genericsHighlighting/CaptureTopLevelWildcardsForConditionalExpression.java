import java.util.*;
class Bug {
    void foo(Double d) {
       <error descr="Incompatible types. Found: 'java.util.List<java.lang.Class<capture<? extends java.lang.Object>>>', required: 'java.util.List<java.lang.Class<?>>'">List<Class<?>> list = Arrays.asList(d == null ? Object.class : d.getClass());</error>
    }
}