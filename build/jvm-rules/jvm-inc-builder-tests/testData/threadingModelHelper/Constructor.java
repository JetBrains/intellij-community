package main;

import com.intellij.util.concurrency.annotations.fake.RequiresEdt;

public class Constructor {
  private final String myField;

  @RequiresEdt
  public Constructor() {
    myField = "text";
  }
}