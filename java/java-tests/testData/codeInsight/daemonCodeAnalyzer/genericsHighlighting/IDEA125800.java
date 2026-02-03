import java.util.List;

abstract class Test {
  abstract <T extends List<String> & Runnable> T list();

  public void test()
  {
    for (String s : list()) {}
  }
}
