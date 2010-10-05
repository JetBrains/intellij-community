// "Change signature of 'f(int...)' to 'f(int..., String)'" "false"
 public class S {
     void f(int... args) {
     f(1,1, "")<caret>;
     }
 }
