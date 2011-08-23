// "Add 'int' as 2nd parameter to method 'f'" "true"
 public class S {
     void f(int k, int i, int... args) {
     f(1,1,null)<caret>;// -> f(1,1,null)
     }
 }
