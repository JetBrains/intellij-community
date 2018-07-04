<error descr="Class 'MyEnumTest' must either be declared abstract or implement abstract method 'm()' in 'MyEnumTest'">enum MyEnumTest</error> {
  ;
  public abstract void m();
}

<error descr="Class 'WithoutConstantInitializer' must either be declared abstract or implement abstract method 'm()' in 'WithoutConstantInitializer'">enum WithoutConstantInitializer</error> {
  FIRST;
  public abstract void m();
}