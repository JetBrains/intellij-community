interface Super
{
    default void method()
    {
        System.out.println("Super.method()");
    }
}

interface Sub extends Super {
    static void foo() {
          <error descr="'Super' is not an enclosing class">Super.super</error>.method();
    }
}
