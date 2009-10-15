public class TestClass {
    TestClass(TestClass p){
       new TestClass((<caret>AAA)obj).f();
    }
}