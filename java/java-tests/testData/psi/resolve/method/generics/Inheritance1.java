
class Test{

    class A <T>{
        B<T> x;
        class B <Z>{
            public void put(T x, Z y){}
            public Z getZ(){}
            public T getT(){}
        }
    }

    {
        new A<Integer>().x.getT().<ref>byteValue();
    }
}