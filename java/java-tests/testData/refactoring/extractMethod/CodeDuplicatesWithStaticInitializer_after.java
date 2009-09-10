class C {
   static C c;
   static {
        java.util.ArrayList<C> l = null;
        l.add(newMethod());
    }

    void foo() {
      System.out.println(newMethod());
    }

    private static C newMethod() {
        return c;
    }
}