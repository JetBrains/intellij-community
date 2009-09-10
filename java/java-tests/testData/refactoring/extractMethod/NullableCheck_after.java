class Test {
   Object foo() {
       Object o = newMethod();
       if (o == null) return null;
      System.out.println(o);
      return o;
   }

    private Object newMethod() {
        Object o = "";
        for (int i = 0; i < 5; i++) {
           if (i == 10){
              o = null;
           }
        }
        if (o == null) {
            return null;
        }
        return o;
    }
}