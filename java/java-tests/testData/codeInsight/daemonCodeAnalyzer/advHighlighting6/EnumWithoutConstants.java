enum MyEnumTest {
  ;
  public <error descr="Abstract method in non-abstract class">abstract</error> void m();
}

enum WithoutConstantInitializer {
  <error descr="Enum constant 'FIRST' must implement abstract method 'm()' in 'WithoutConstantInitializer'">FIRST</error>;
  public abstract void m();
}