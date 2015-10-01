abstract class Main {

    private static String notification;

    public static void main(String notification) throws Exception {
        Main.notification = notification;
        System.out.println(
      "Too large notification " + Main.notification + 
      " of " + notification.getClass() + 
      "\nListener=" + notification.substring(0));
  }

}