class Test {
    int method(boolean cond1, boolean cond2) {
         <selection>try {
             if(cond1) return 0;
             else if(cond2) return 1;
             return 27;
         } finally {           
             doSomething();
         }</selection>
         return 12;
    }
  void doSomething() {}
}