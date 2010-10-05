// "Change signature of 'f(int...)' to 'f(String, int...)'" "true"
 public class S {
     void f(int... args) {
     f("",null)<caret>;
     }
 }
