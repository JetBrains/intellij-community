class C {
   static C c;
   static {
        java.util.ArrayList<C> l = null;
        l.add(c);
    }

    void foo() {
      System.out.println(<selection>c</selection>);
    }
}