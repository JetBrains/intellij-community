public class TestReturnType1 {
    class A<T extends Runnable>{
        public T foo(T t){
            return null;
        }
    }
    {
        new A<String>().foo(new String()).<caret>toCharArray();
    }
}
