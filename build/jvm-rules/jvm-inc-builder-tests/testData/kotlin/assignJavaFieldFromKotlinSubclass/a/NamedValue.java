public abstract class NamedValue {
  
  protected String myName;

  protected NamedValue(String name) {
    myName = name;
  }

  public final String getName() {
    return myName;
  }
}