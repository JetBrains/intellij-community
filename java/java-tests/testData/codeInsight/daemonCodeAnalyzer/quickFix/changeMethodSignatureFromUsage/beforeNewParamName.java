// "Add 'int' as 1st parameter to method 'f'" "true-preview"
 public class S {
     void f(String args) {

     }

     void bar() {
       f(1<caret>1, "");
     }
 }
