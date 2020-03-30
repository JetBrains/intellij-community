class Test {
   void m(boolean b) {
           int x = 42;
           try {
               <selection>
               if(b) {
                   x = 23;
                   throw new Exception();
               }
               </selection>
           } catch(Exception e) {
               System.out.println(x);
           }
       }

}