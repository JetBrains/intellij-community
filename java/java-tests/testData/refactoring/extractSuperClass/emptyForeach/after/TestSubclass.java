import java.util.List;

class TestSubclass extends Test {
}


class Base {
  public void main(List<Test> tests) {
    for (Test test : tests) {}
  }
}

class Inheritor extends Base {
  @Override
  public void main(List<Test> tests) {
    super.main(tests);
  }
}
