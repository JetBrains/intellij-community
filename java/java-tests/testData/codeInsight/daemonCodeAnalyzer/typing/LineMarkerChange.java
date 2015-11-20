 import java.util.concurrent.Callable;

 /**
  * Created by IntelliJ IDEA.
  * User: cdr
  * Date: May 19, 2009
  * Time: 2:59:31 PM
  * To change this template use File | Settings | File Templates.
  */
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
