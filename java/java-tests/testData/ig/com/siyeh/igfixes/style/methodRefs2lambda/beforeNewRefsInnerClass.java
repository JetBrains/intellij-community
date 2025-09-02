// "Replace method reference with lambda" "true-preview"
class MyTest {
    static class Inner {
        Inner(MyTest mt) {};
    }
  
    interface I {
        Inner m(MyTest receiver);
    }

    static {
        I i1 = MyTest.Inner:<caret>:new;
    }
}
