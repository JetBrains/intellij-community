// "Add explicit type arguments" "true"
import java.util.*;

class Test {
   
    void someMethod(boolean b) { 
      List<String> s = b ? Foo.f<caret>() : new ArrayList<String>(); 
    }
}

class Foo{
  static <T> List<T> f() { return new ArrayList<T>(); }
}