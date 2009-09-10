import javax.swing.JComponent;

class IdentityComplete {
  private int myField;

  public void <caret>method() {
    class MethodLocal {
      private int myMethodLocalInt;
      public void methodLocalInc() {
        myMethodLocalInt++;
      }
    }

    JComponent methodAnonymous = new JComponent() {
      private int myMethodAnonymousInt;
      public void methodAnonymousInc() {
        myMethodAnonymousInt++;
      }
    };
  }

  public void context() {
    class ContextLocal {
      private int myContextLocalInt;
      public void contextLocalInc() {
        myContextLocalInt++;
      }
    }

    JComponent contextAnonymous = new JComponent() {
      private int myContextAnonymousInt;
      public void contextAnonymousInc() {
        myContextAnonymousInt++;
      }
    };
  }
}
