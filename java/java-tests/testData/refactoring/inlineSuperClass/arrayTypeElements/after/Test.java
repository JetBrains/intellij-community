class Test {
  public Test() {
  }

    public static Test[] getArray() {
      return new Test[0];
    }

    public static Test[] getArrayWithInitializer() {
      return new Test[]{};
    }
}