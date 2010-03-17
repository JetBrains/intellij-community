package com.package1;

public class Class2 {
  private boolean myField1;
  public boolean myField2;
  public boolean myField3;
  public boolean myField4;

  public int getValue() {
    return 0;
  }

  public class InnerClass1 {
    public int myInnerClassField;

    public class InnerClass12 {
      public int myInnerClassField;

      public class InnerClass13 {
        public int myInnerClassField;

        public class InnerClass14 {
          public int myInnerClassField;

          public class InnerClass15 {
            public int myInnerClassField;
          }
        }
      }
    }
  }

  public class InnerClass2 {
    public int myInnerClassField;

    public class InnerClass22 {
      public int myInnerClassField;

      public class InnerClass23 {
        public int myInnerClassField;

        public class InnerClass24 {
          public int myFieldToSelect;

          public class InnerClass25 {
            public int myInnerClassField;
          }
        }
      }
    }
  }

}
