 public abstract class BaseTest
 {
     private Object f() {
         return null;
     }

 }
 class S {
     private Object f() {
         new BaseTest() {
             {
                 <ref>f(); 
             }
         };

         return null;
     }
 }
