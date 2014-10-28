package foo;

import javax.annotation.Nullable;

class C {
  @Nullable
  private String a;
  private String b;

  public void setA(String a) {
    this.a = a;
  }

  public void setB(@Nullable String b) { // doesn't lead to a warning because b field is not a parameter
    this.b = b;
  }
}