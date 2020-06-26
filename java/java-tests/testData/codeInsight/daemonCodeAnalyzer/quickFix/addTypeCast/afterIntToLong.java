// "Cast parameter to 'long'" "true"
class a {
 void test(Long l) {}
 
 void foo() {
   test(123L);
 }
}

