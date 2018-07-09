// "Change 'new Runnable() {...}' to 'new StringBuffer()'" "true"

class X {
 public StringBuffer buf = <caret>new Runnable//comment0
    ()
    //comment1
  {//comment2
    public void run(){
      System.out.println("smth");
    }
  };
 }
