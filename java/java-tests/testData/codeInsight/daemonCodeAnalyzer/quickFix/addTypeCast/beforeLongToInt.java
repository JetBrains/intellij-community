// "Convert argument to 'int'" "true-preview"
class a {
 void test(int d) {}
 
 void foo() {
   test(<caret>123L);
 }
}

