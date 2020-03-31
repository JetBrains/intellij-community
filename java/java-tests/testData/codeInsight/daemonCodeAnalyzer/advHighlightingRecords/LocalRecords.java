class Foo {
  void test() {
    record NoComponent() {}
    
    static record DeclaredStatic() {}
    
    <error descr="Modifier 'private' not allowed here">private</error> record Private() {}
  }
  
  static int STATIC_VAR = 3;
  
  int instanceVar;
  
  void capture() {
    int outerLocal = 2;
    final int compileTimeConstant = 3;
    record Local() {
      static int X = 2;
      
      void test() {
        System.out.println(STATIC_VAR);
        System.out.println(<error descr="Non-static field 'instanceVar' cannot be referenced from a static context">instanceVar</error>);
        System.out.println(<error descr="Non-static variable 'outerLocal' cannot be referenced from a static context">outerLocal</error>);
        System.out.println(X);
        System.out.println(compileTimeConstant);
      }
    }
  }
}