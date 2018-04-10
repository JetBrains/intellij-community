class Test {
  
    <A extends B, B extends C, C extends A> void f(C c) {
        newMethod(c);
    }

    private <A extends B, B extends C, C extends A> void newMethod(C c) {
        System.out.println(c);
    }
}