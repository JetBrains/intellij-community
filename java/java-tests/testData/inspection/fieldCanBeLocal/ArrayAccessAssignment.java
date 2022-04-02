class ArrayAccessAssignment
{
  private final TestData settings;

  public ArrayAccessAssignment(TestData settings)
  {
    this.settings = settings;
  }

  public void doStuff()
  {
    settings.array[1] = 1;
  }

  private static class TestData
  {
    public int[] array = new int[10];
  }
}