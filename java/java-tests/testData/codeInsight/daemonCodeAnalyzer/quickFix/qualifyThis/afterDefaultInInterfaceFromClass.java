// "Qualify super expression with 'Super'" "true"
interface Super
{
    default void method()
    {
        System.out.println("Super.method()");
    }
}

class Sub implements Super {
    void foo() {
          Super.super.method();
    }
}