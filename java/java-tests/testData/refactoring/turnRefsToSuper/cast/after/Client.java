class Client {
   I getI() { return null; };

   void method() {
        I a = getI();
   }

   int anotherMethod() {
       return getI().method();
   }
}