// "Change 'new Runnable() {...}' to 'new StringBuffer()'" "true"

class X {
 public StringBuffer buf = <caret>new Runnable(){
    public void run(){
      System.out.println("smth");
    }
  };
 }
