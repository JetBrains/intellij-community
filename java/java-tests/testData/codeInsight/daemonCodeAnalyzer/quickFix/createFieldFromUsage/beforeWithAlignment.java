// "Create field 'field'" "true-preview"
class A {
  public static final String NOTIFICATION_ENABLED     = "NOTIFICATION_ENABLED";
  public static final String NOTIFICATION_DEVICE_NAME = "NOTIFICATION_DEVICE_NAME";

  public static String getString(String key)
  {
      return getString(key, "");
  }

  public static String getString(String key, String defaultValue)
  {
      return null;
  }
  
}

class B {
  {
    String s = A.fi<caret>eld;
  }
}
