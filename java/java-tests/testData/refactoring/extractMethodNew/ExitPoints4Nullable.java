class Test {
    Integer method() {
         <selection>try {
             if(cond1) return 0;
             else if(cond2) return null;
             System.out.println("Text");
         } finally {           
             doSomething();
         }</selection>
         return 12;
    }
}