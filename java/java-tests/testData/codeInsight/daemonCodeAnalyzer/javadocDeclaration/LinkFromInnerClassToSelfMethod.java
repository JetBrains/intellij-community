class Test {

     public void i(int ppp) {}
     /**
      * {@link #<error descr="Cannot resolve symbol 'foo(int)'">foo</error>(int)}
      * {@link #foo()}
      * {@link #i(int)}
     */
     class A{
       public void foo() {}
     }

}