// "Override method 'm'" "true"
interface A {
  default void <caret>m(){}
}

interface B extends A {}