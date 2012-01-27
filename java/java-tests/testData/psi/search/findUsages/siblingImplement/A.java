class A {
    public void foo(Object o) {
    }

    interface I {
         public void foo(Object o);  //This should be consideredd implemented in A
    }

}

class B extends A implements A.I {
}