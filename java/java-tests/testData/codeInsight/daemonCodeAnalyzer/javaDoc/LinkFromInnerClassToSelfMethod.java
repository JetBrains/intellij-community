class Test {

     public void i(int ppp) {}
     /**
      * {@link #<error>foo(int)</error>}
      * {@link #foo()}
      * {@link #i(int)}
     */
     class A{
       public void foo() {}
     }

}