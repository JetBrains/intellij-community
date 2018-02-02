package ppp;

import qqq.Util;

public class Client {
  private static class MyUtil extends Util {
    public static void perform() {
      BaseImpl impl = new BaseImpl() {
        @Override
        protected void init() {
          System.out.println(myMessage);
        }
      };
    }
  }
}
