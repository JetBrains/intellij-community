public class TestGenericMethodOverloading2 {
    class A<T>{
        public boolean equals(T t){
            return false;
        }
    }
    {
        new A().<caret>equals(new Object());
    }
}
