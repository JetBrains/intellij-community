// "Add explicit type arguments to then-branch call" "true-preview"
import java.util.*;

class Test {
   
    void someMethod(boolean b) { 
      List<String> s = b ? Foo.<String>f() : new ArrayList<String>(); 
    }
}

class Foo{
  static <T> List<T> f() { return new ArrayList<T>(); }
}