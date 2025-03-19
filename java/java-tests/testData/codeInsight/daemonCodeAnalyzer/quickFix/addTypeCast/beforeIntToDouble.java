// "Convert argument to 'double'" "true-preview"
class a {
 void test(Double d) {}
 
 void foo() {
   test(<caret>123);
 }
}

