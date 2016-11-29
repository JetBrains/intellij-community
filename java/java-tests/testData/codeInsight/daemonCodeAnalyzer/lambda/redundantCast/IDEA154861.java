import java.util.Set;

class Test
{

  @SuppressWarnings("unchecked")
  public void someMethod(final Set<String> set)
  {
    when(set)
      .thenAnswer(invocationOnMock -> union(set, (Set<String>) invocationOnMock.getArguments()[0]));
  }


  static <T> OngoingStubbing<T> when(T methodCall)
  {
    return answer -> null;
  }

  interface OngoingStubbing<T>
  {
    OngoingStubbing<T> thenAnswer(Answer<?> answer);
  }

  interface Answer<T> {
    T answer(InvocationOnMock invocation) throws Throwable;
  }

  interface InvocationOnMock
  {
    Object[] getArguments();
  }

  static <E> Set<E> union(final Set<? extends E> set1, final Set<? extends E> set2) {
    return null;
  }
}
