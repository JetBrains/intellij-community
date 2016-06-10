interface I {
  void m();
}

<error descr="Class 'A' must either be declared abstract or implement abstract method 'm()' in 'I'">class A implements I</error> {}

class B extends A {}

class U {
  {
    new B() {};
    B b = new B();
  }
}