
import java.util.List;

class MyTest {

  public void testLong(List<Long> list, int i) {
    assertEquals(1L, (i >= 0 ? (long) getLong(list, i) : 2L));
    assertEquals(1L, i >= 0 ? (long) getLong(list, i) : 2L);

    assertEquals(1L, (i >= 0 ? (<warning descr="Casting 'list.get(...)' to 'long' is redundant">long</warning>) list.get(i) : 2L));
    assertEquals(1L, i >= 0 ? (<warning descr="Casting 'list.get(...)' to 'long' is redundant">long</warning>) list.get(i) : 2L);
  }

  private <T> T getLong(List<T> list, int i) {
    return list.get(i);
  }

  private static void assertEquals(long l, long l1) { }
  private static void assertEquals(Object l, Object l1) { }

}
