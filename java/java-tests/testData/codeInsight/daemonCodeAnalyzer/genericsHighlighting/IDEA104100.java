class TestGenericMap<A extends Comparable<A>, B extends Comparable<B>>
{
  public TestGenericMap<B, A> inverse()
  {
    return new TestGenericMap<>();
  }
}