// "Qualify super expression with 'Super'" "true-preview"
interface Super
{
    default void method()
    {
        System.out.println("Super.method()");
    }
}

class Sub implements Super {
    void foo() {
          super.me<caret>thod();
    }
}