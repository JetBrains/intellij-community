// "Cast parameter to 'int'" "true"
class a {
 void test(int d) {}
 
 void foo() {
   test(<caret>123L);
 }
}

