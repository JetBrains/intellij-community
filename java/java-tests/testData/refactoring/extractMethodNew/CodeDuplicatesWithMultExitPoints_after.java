class A {
   int foo (Object o) {
       if (newMethod(o)) return 0;
       if (newMethod(o)) return 0;
       return 1;
   }

    private boolean newMethod(Object o) {
        if (o == null) return true;
        return false;
    }
}