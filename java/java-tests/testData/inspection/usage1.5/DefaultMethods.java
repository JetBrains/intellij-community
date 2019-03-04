import java.util.Iterator;

public class <error descr="Default method 'remove' is not overridden. It would cause compilation problems with JDK 6">DefaultMethods</error> implements Iterator<String> {
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
    return (T) <error descr="Cannot resolve method 'iterator()'">iterator</error>();
  }

  {
    Iterator<String> it=new <error descr="Default method 'remove' is not overridden. It would cause compilation problems with JDK 6">Iterator<String></error>(){
      @Override
      public boolean hasNext(){
        return false;
      }
      @Override
      public String next(){
        return null;
      }
    };
  }
}

