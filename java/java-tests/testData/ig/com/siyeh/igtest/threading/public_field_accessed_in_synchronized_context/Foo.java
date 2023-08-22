package com.siyeh.igtest.threading.public_field_accessed_in_synchronized_context;

import java.util.List;

class Bar2 {
  public String field1;
  private String field2;

  public void setField2(String s) {
    field2 = s;
  }
}
public class Foo {
  public Bar2 myBar;
  private List<Bar2> myBars;

  synchronized public void setSingle() {
    <warning descr="Non-private field 'myBar' accessed in synchronized context">myBar</warning>.field1 = "bar";
    <warning descr="Non-private field 'myBar' accessed in synchronized context">myBar</warning>.setField2("bar");
  }

  synchronized public void setViaList() {
    myBars.iterator().next().field1 = "bar";
    myBars.iterator().next().setField2("bar");
  }
}
