class Test {
  public static boolean test(String key, String keyValue){
    boolean b;
    <caret>if(key != null /*5*/&& key.equals(keyValue)) {
              // some comment goes here
              b/*1*/ = /*2*/true;
          } else {
              b/*3*/ = /*4*/false;
          }
      }
}