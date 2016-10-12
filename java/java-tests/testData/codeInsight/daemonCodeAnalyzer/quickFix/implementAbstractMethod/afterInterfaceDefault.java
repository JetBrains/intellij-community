// "Override method 'm'" "true"
interface A {
  default void m(){}
}

interface B extends A {
    @Override
    default void m() {
        
    }
}