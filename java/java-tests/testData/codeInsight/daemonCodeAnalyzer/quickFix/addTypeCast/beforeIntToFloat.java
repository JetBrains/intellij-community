// "Convert argument to 'float'" "true-preview"
class a {
 void test(Float f) {}
 
 void foo() {
   test(<caret>123);
 }
}

