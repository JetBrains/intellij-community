class Test {
  public void closeable(AutoCloseable y) {
    try(<error descr="Resource references are not supported at language level '7'">y</error>) {
      System.out.println("Hello");
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public void incompleteCode(AutoCloseable y) {
    try(<error descr="Resource references are not supported at language level '7'">y</error><error descr="')' expected"> </error>{
      System.out.println("Hello");
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}