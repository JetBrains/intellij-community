package my.impl;
import my.api.MyService;

public class MyServiceImpl implements MyService {
  public MyServiceImpl(Object... objects) {System.out.println(objects);}
  @Override
  public void foo() {}
}