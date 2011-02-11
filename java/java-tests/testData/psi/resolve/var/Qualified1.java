public class Test{
  int a = 0;
}

class Test1 {
  static Test test = new Test();
  static {
    System.out.println("" + test.<ref>a);
  }
}
