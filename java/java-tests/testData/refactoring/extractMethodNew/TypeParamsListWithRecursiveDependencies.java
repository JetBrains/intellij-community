class Test {
  
    <A extends B, B extends C, C extends A> void f(C c) {
        <selection>System.out.println(c);</selection>
    }
}