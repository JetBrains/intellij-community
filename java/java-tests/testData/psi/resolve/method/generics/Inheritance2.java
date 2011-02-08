
class Test{

    class A <T>{
        B x;
        class B <Z>{
            public void put(T x, Z y){}
            public Z getZ(){}
            public T getT(){}
        }
    }

    {
        new A<Integer>().new B<Boolean>().getZ().<ref>booleanValue();
    }
}