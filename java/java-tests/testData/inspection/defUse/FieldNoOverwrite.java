public class FieldNoOverwrite {
  private String myField;
  private String myNext;
  private FieldNoOverwrite[] myChildren;

  void use() {
    myField = myNext;
    myNext = null;
  }

  void use2() {
    myField = myNext;
    myNext = null;
    System.out.println(myField);
  }

  void withValue(String value, Runnable r) {
    String oldValue = myField;
    myField = value;
    try {
      r.run();
    }
    finally {
      myField = oldValue;
    }
  }

  void writeAtBranches(boolean b) {
    myField = "1";
    if (b) {
      myNext = myField;
    }
    myField = "2";
  }
  
  void clearAll(FieldNoOverwrite[] children) {
    for (FieldNoOverwrite child : myChildren) child.myField = "1";
    myChildren = children;
    for (FieldNoOverwrite child : children) child.myField = "2";
  } 
}