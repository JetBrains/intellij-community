class Test {
   Object foo() {
      <selection>Object o = "";
      for (int i = 0; i < 5; i++) {
         if (i == 10){
            o = null;
         }
      }
      if (o == null) {
        return null;
      }</selection>
      System.out.println(o);
      return o;
   }
}