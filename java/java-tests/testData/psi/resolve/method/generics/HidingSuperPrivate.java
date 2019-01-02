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
                 <caret>f(); 
             }
         };

         return null;
     }
 }
