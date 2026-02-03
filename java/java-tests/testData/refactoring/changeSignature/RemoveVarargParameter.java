class Test {
   void fo<caret>o(int i, int ... ja){
    }

    void bar() {
      foo(0);
      foo(0, 1);
      foo(0, 1, 2);
      foo(0, new int[]{3, 4});
      foo(0, new int[0]);
    }
}