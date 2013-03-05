// "Change 'new Runnable() {...}' to 'new StringBuffer()'" "true"

class X {
 public StringBuffer buf = new StringBuffer() {
     public void run(){
       System.out.println("smth");
     }
 };
 }
