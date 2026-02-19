class IncorrectModifier {
  X x = (sealed var y) -> System.out.println() 
  
  interface X {
    void x(String s);
  }
}