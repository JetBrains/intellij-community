import org.jetbrains.annotations.*;

class Test {
  @NotNull String str1;
  <warning descr="Not-null fields must be initialized">@NotNull</warning> String str2;
  @NotNull String str3;
  <warning descr="Not-null fields must be initialized">@NotNull</warning> String str4;
  
  Test() {
    init();
    this.initStr3();
    new Test().initStr4();
  }
  
  Test(String s) {
    this();
    str2 = s;
  }
  
  void initStr3() {
    str3 = "";
  }
  
  void initStr4() {
    str4 = "";
  }
  
  void init() {
    if (Math.random() > 0.5) {
      str1 = "";
      str2 = "";
    } else {
      str1 = "123";
    }
  }
}