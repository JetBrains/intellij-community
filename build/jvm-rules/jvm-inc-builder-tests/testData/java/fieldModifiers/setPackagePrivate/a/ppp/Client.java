package ppp;

import qqq.BaseImpl;

public class Client {
  public static void perform() {
    BaseImpl impl = new BaseImpl() {
      @Override
      protected void init() {
        System.out.println(myMessage);
      }
    };
  }
}
