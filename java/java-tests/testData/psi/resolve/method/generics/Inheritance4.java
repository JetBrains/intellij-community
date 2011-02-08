
class Test{

    class A <T>{
        B x;
        class B <Z>{
            public void put(T x, Z y){}
            public Z getZ(){}
            public T getT(){}
        }
    }

    class C extends A<Integer>{}

    {
        new C().new B<Boolean>().getT().<ref>byteValue();
    }
}