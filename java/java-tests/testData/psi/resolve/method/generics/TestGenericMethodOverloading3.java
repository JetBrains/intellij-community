public class TestGenericMethodOverloading3 {
    class A<T>{
        public boolean equals(T t){
            return false;
        }
    }
    {
        new A<String>().<caret>equals(new Object());
    }
}
