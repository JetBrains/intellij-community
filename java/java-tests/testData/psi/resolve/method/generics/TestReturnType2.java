public class TestReturnType2 {
    class A<T extends Runnable>{
        public T foo(T t){
            return null;
        }
    }
    {
        new A().foo(new String()).<caret>toCharArray();
    }
}
