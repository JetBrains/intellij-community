// IDEA-181860
class bugcheck {
  private static class MyAutoCloseable implements AutoCloseable {
    @Override public void close() throws MyClosingException {}
    Long count() { return 0L; }
    Long countClose() throws MyClosingException { return 0L; }
    private class MyClosingException extends Exception {}
  }

  private static Object nullCheck() {
    Long size = null;
    try (MyAutoCloseable autoCloseable = new MyAutoCloseable()) {
      size = autoCloseable.count();
      return size;
    } catch (MyAutoCloseable.MyClosingException e) {
      return size;
    }
  }

  private static boolean nullCheck1() {
    Long size = null;
    try (MyAutoCloseable autoCloseable = new MyAutoCloseable()) {
      size = autoCloseable.count()+1;
    } catch (MyAutoCloseable.MyClosingException e) {
      return <warning descr="Condition 'size == null' is always 'false'">size == null</warning>;
    }
    return <warning descr="Condition 'size == null' is always 'false'">size == null</warning>;
  }

  private static Object nullCheck2() {
    Long size = null;
    try (MyAutoCloseable autoCloseable = new MyAutoCloseable()) {
      size = autoCloseable.countClose();
      return size;
    } catch (MyAutoCloseable.MyClosingException e) {
      return <warning descr="Expression 'size' might evaluate to null but is returned by the method which is not declared as @Nullable">size</warning>;
    }
  }
}