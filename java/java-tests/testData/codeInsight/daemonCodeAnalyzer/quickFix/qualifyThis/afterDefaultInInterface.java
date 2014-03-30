// "Qualify super expression with 'Super'" "true"
interface Super
{
    default void method()
    {
        System.out.println("Super.method()");
    }
}

interface Sub extends Super {
    default void foo() {
          Super.super.method();
    }
}