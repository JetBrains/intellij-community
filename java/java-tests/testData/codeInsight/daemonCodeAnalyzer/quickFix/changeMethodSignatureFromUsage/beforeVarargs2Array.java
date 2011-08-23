// "Add 'String' as 2nd parameter to method 'f'" "false"
 public class S {
     void f(int... args) {
     f(1,1, "")<caret>;
     }
 }
