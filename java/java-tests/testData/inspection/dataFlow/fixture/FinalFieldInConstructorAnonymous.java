class SomeClass {
  private final Object myField;
  
  {
    if (<error descr="Variable 'myField' might not have been initialized">myField</error> != null) { // false-positive report 'condition is always true'
      System.out.println(myField.toString());
    }
  }

  public SomeClass(SomeOtherClass o) {
    o.invoke(new Runnable() {
      public void run() {
        if (myField != null) { // false-positive report 'condition is always true'
          System.out.println(myField.toString());
        }
      }
    });
    myField = "xxx";
  }
}

class SomeOtherClass {
  public void invoke(Runnable r) {
    r.run();
  }
}
