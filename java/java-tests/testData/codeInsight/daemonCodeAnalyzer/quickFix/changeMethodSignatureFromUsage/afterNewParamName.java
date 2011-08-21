// "Add 'int' as 1st parameter to method 'f'" "true"
 public class S {
     void f(int i, String args) {

     }

     void bar() {
       f(11, "");
     }
 }
