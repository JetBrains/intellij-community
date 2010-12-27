// "Change signature of 'f(String)' to 'f(int, String)'" "true"
 public class S {
     void f(String args) {

     }

     void bar() {
       f(1<caret>1, "");
     }
 }
