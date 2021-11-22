// "Cast argument to 'float'" "true"
class a {
 void test(Float f) {}
 
 void foo() {
   test(<caret>123);
 }
}

