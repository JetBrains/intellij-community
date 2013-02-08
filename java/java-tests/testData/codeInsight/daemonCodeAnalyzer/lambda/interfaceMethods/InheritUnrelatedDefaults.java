interface B { default void foo() {} }
interface C { default void foo() {} }
class <error descr="D inherits unrelated defaults for foo() from types B and C">D</error> implements B, C {}
