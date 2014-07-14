class IdeaBugTest {
    interface A<T> {
    }
  
    class B implements A<int[]> {
    }
  
    void test(A<?> a) {
       B b = (B)a;
    }
}