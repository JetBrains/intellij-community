class A extends Test{
   void foo(Param param) {
     super.foo(param);
     System.out.println(param.setI(param.getI() + 1));
   }

   void bazz(){
     foo(new Param(0));
   }
}