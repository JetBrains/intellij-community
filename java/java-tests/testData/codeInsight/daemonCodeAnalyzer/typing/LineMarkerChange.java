 import java.util.concurrent.Callable;

 class LineMarkers implements Runnable, Callable, CharSequence{
    <caret>





     public void run() {
         //To change body of implemented methods use File | Settings | File Templates.
     }










     public Object call() throws Exception {
         return this;
     }











     public int length() {
         return 0;  //To change body of implemented methods use File | Settings | File Templates.
     }







     public char charAt(int index) {
         return 0;  //To change body of implemented methods use File | Settings | File Templates.
     }

     public CharSequence subSequence(int start, int end) {
         return toString();
     }
 }
