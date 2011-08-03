// "Add int as 1 parameter to method f" "true"
 public class S {
     void f(String args) {

     }

     void bar() {
       f(1<caret>1, "");
     }
 }
