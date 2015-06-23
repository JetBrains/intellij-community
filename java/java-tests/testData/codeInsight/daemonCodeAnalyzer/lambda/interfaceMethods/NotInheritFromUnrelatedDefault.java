interface FirstParent {

    default int doSomething() {
        return 1;
    }
}

interface SecondParent {

    int doSomething();
}

class <error descr="Class 'SecondParent' must either be declared abstract or implement abstract method 'doSomething()' in 'SecondParent'">FirstSon</error> implements FirstParent, SecondParent {}

<error descr="Class 'SecondSon' must either be declared abstract or implement abstract method 'doSomething()' in 'SecondParent'">class <error descr="SecondSon inherits abstract and default for doSomething() from types SecondParent and FirstParent">SecondSon</error> implements SecondParent, FirstParent</error> {}

interface A {
  default int foo() {
    return 1;
  }
}

interface B {
  abstract int foo();
}

interface <error descr="C inherits abstract and default for foo() from types A and B">C</error> extends A, B {
}
