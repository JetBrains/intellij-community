import java.util.*;
import java.io.*;

class X {
  public void save(Collection<? extends Long> value) throws IOException {
    assert value.isEmpty() || value.size() == 1;
    writeLONG(value.isEmpty() ? 0 : value.iterator().next());
  }
  public static void writeLONG(long val) throws IOException {
  }
}