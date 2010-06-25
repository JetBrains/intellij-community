// "Change signature of 'f(int, int...)' to 'f(int, int, int...)'" "true"
 public class S {
     void f(int k, int i, int... args) {
     f(1,1,null)<caret>;// -> f(1,1,null)
     }
 }
