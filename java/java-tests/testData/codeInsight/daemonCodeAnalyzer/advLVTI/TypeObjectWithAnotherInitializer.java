class TypeObjectWithAnotherInitializer {
  class X {}
  class Y {
    public void main(String[] args) {
      var x = new X() {};
      <error descr="Incompatible types. Found: 'TypeObjectWithAnotherInitializer.Y', required: 'anonymous TypeObjectWithAnotherInitializer.X'">x = new Y()</error>;
    }
  }

}