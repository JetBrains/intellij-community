 public class T2 {
     int <flown111111>fff;

     {
 //        f(fff);
     }

 void f(int <caret>i2) {
     int i = -1;
     i = 0;
     try {           //
         i = 1;
         if (i2 == 0) {
             p(<flown1>i2);//
         }
     }
     catch (Exception e) {

     }
     finally {
         i = 9;
     }
 }

     public void p(int <flown11>i) {//
 //        fff = i;
         int <flown1111>ddd = <flown111>i;
         fff =<flown11111>ddd;
     }
 }
