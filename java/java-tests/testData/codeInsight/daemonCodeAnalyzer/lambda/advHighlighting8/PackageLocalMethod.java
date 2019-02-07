package foo.bar;
<error descr="Class 'C' must either be declared abstract or implement abstract method 'foo()' in 'A'">class C extends B</error> {}

<error descr="Class 'C1' must either be declared abstract or implement abstract method 'foo()' in 'A'">class C1 extends B</error>{
  public void foo(){}
}
<error descr="Class 'C2' must either be declared abstract or implement abstract method 'foo()' in 'A'">class C2 extends B</error> {
  public int foo() throws java.io.IOException {
    return 0;
  }
}