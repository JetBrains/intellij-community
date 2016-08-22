import org.springframework.util.Assert;

class Contracts {

  void foo(Object o) {
    Assert.isTrue(o instanceof String);
    String s = (String) o;
  }

  void foo1(Object o) {
    Assert.state(o instanceof String, "oops");
    String s = (String) o;
  }

}