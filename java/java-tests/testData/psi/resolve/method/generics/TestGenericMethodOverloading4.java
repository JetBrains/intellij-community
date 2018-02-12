public class TestGenericMethodOverloading4 {
    class A<T>{
        public boolean equals(T t){
            return false;
        }
    }
    {
        new A<String>().<ref>equals(new String());
    }
}
