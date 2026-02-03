package deprecated;

public class DeprecatedOwner {
  @Deprecated
  public static final int DEPRECATED_CONSTANT = 42;

  @Deprecated
  public String deprecatedField;

  //This method is not deprecated, but its overloading is.
  public void deprecatedMethod(int x) {
  }

  @Deprecated
  public void deprecatedMethod(String s) {
  }
}