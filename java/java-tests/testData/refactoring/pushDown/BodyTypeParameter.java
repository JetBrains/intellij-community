class Test <T> {
   void <caret>foo(T t) {
     T tt = t;
   }
}

class I extends Test<String>{}