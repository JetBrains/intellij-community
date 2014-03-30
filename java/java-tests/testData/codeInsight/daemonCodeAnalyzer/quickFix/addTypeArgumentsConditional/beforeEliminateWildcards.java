// "Add explicit type arguments" "true"
import java.util.*;

class Test {
     static class A {}
     static class B extends A {}
 
 
     public Collection<? extends A> run(boolean flag) {
         return flag ? Collections.singletonList(new B()) : Collections.<caret>emptyList();
     }
}