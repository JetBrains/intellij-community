class Test {
  public static boolean test(String key, String keyValue){
    <caret>if(key != null && key.equals(keyValue)) {
              // some comment goes here
              return true;
          } else {
              return false;
          }
      }
}