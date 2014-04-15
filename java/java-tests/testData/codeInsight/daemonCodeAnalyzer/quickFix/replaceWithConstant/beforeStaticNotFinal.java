// "Replace with 'A.RADIUS'" "true"

class A {
  public static String RADIUS = "radius";

  public void myMethod(String propertyName) {
    if ("rad<caret>ius".equals(propertyName)) {
    }
  }
}