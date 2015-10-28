import java.util.Iterator;

public class Test implements Iterator<String> {
  @Override
  public boolean hasNext() {
    return false;
  }

  @Override
  public String next() {
    return null;
  }

  static class T implements Iterator<String> {
    @Override
    public boolean hasNext() {
      return false;
    }

    @Override
    public String next() {
      return null;
    }

    @Override
    public void remove() {}
  }

  public <T extends Iterator<String>> T typedIterator() {
    return (T) iterator();
  }
}

