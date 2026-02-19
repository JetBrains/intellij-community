public class BoxedBooleanMethodWithCast {
  // IDEA-343951
  public static boolean checkObjectType(Object object) {
    if (object instanceof Boolean) {
      final boolean booleanValue = meansTrue((Boolean) object);
      return booleanValue;
    }
    return false;
  }

  public static void main(String[] args) {
    System.out.println("True = " + checkObjectType(Boolean.TRUE));
    System.out.println("False = " + checkObjectType(Boolean.FALSE));
  }

  public static boolean meansTrue(Boolean bool) {
    return Boolean.TRUE.equals(bool);
  }
}