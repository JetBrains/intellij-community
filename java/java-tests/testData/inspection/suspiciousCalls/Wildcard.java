import java.util.List;
import java.util.ArrayList;

class Clazz {
    void foo(List<? extends Number> l) {
      boolean b = l.contains(<warning descr="'List<capture of ? extends Number>' may not contain objects of type 'String'">""</warning>);    }
    void bar() {
      List<? extends Class<?>> l = new ArrayList<Class<?>>();
      Class<?> o = String.class;
      int i = l.indexOf(o);
    }
}