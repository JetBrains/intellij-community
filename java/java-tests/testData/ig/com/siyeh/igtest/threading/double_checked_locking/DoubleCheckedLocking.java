import java.io.StringWriter;

// IDEA-237477
class DoubleCheckedLocking {
  private static final StringWriter STRING_WRITER = new StringWriter();
  private static final StringBuffer BUFFER = STRING_WRITER.getBuffer();
  private static final int MAX_BUFFER_LENGTH = 10_000_000;

  static void log() {
    if (BUFFER.length() > MAX_BUFFER_LENGTH) {
      synchronized (BUFFER) {
        if (BUFFER.length() > MAX_BUFFER_LENGTH) {
          BUFFER.delete(0, BUFFER.length() - MAX_BUFFER_LENGTH + MAX_BUFFER_LENGTH / 4);
        }
      }
    }
  }
}
// https://stackoverflow.com/questions/49741933
class DoubleCheck {

  private static volatile Integer v_instance = null;
  /** non-volatile instance. */
  private static Integer n_instance = null;

  public static void getVolatileInstance()
  {
    if (v_instance == null)
    {
      // thread safe singleton
      synchronized (DoubleCheck.class)
      {
        if (v_instance == null) // doubly check
        {
          assignVolatile(5);
        }
      }
    }
  }
  public static void getNonVolatileInstance()
  {
    <warning descr="Double-checked locking"><caret>if</warning> (n_instance == null)
    {
      // thread safe singleton
      synchronized (DoubleCheck.class)
      {
        if (n_instance == null) // doubly check
        {
          assignNonVolatile(6);
        }
      }
    }
  }

  public static void assignVolatile(Integer value)
  {
    v_instance = value;
  }

  public static void assignNonVolatile(Integer value)
  {
    n_instance = value;
  }
}