class Client {
   I getI() { return null; };

   void method() {
        A a = (A) getI();
   }

   int anotherMethod() {
       return ((A) getI()).method();
   }
}