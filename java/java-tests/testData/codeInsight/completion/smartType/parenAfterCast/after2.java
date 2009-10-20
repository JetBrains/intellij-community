public class TestClass {
    TestClass(TestClass p){
       new TestClass((TestClass) <caret>obj).f();
    }
}