// "Change 'new Runnable() {...}' to 'new StringBuffer()'" "true-preview"

class X {
    //comment0
    //comment1
    public StringBuffer buf = new StringBuffer() {//comment2
        public void run(){
          System.out.println("smth");
        }
    };
 }
