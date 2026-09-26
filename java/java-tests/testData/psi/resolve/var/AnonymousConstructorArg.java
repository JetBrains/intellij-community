class Test {
  static Test createTest(int value) {
    return new Test(<caret>value) {};
  }

  int value;

  Test(int value) {
  }
}
