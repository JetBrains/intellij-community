// "Cast parameter to 'int'" "true"
class a {
 void test(int d) {}
 
 void foo() {
   test((int) 123_000_000_000L);
 }
}

