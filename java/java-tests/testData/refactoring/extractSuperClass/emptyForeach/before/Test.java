import java.util.List;

class Test {
  public void m() {}
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
