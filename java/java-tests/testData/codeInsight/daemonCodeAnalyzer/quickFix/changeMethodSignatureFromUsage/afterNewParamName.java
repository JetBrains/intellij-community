// "Change signature of 'f(String)' to 'f(int, String)'" "true"
 public class S {
     void f(int i, String args) {

     }

     void bar() {
       f(11, "");
     }
 }
