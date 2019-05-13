abstract class Main {

    public static void main(String notification) throws Exception {
        System.out.println(
      "Too large notification " + noti<caret>fication + 
      " of " + notification.getClass() + 
      "\nListener=" + notification.substring(0));
  }

}