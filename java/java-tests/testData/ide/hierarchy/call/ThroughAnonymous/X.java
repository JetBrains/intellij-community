package com.hierarchytest;

import java.util.function.Function;

interface AcmClient {
  default String getUser() {
    return null;
  }
}

class AcmClientImpl implements AcmClient {
  @Override
  public String getUser() {
    return returnSmth();
  }

  private String returnSmth() {
    return null;
  }
}

class MethodContext {
  public void call(Function<Object, Object> objectObjectFunction) {
  }
}

class OuterService {
  SomeService someService;

  public void doOutside() {
    someService.doSmth();
  }
}

class OuterService2 {

  SomeService2 someService;

  public void doOutside() {
    someService.doSmth();
  }
}

class SomeService {
  AcmClient acmClient = new AcmClientImpl();

  public void doSmth() {
    MethodContext context = new MethodContext();
    context.call(new Function<Object, Object>() {
      @Override
      public Object apply(Object o) {
        return acmClient.getUser();
      }
    });
  }
}

class SomeService2 {
  AcmClient acmClient = new AcmClientImpl();

  public void doSmth() {
    MethodContext context = new MethodContext();
    context.call(o -> acmClient.getUser());
    context.call(this::ggg);
  }
  Object ggg(Object i ) {
    return acmClient.getUser();
  }
}