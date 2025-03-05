// "Add explicit type arguments to else-branch call" "true-preview"
import java.util.*;

class Test {
     static class A {}
     static class B extends A {}
 
 
     public Collection<? extends A> run(boolean flag) {
         return flag ? Collections.singletonList(new B()) : Collections.<A>emptyList();
     }
}