// "Qualify super expression with 'Super'" "false"
interface Super
{
    void method();
}

interface Sub extends Super {
    default void foo() {
          <caret>super.method();
    }
}