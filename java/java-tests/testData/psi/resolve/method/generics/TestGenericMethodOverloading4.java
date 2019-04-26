public class TestGenericMethodOverloading4 {
    class A<T>{
        public boolean equals(T t){
            return false;
        }
    }
    {
        new A<String>().<caret>equals(new String());
    }
}
