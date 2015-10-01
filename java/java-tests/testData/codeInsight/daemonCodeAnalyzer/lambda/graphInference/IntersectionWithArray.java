
import java.io.Serializable;
import java.util.Collection;

class Issue {

  public static void main(String[] args) {
    consume(get());
    consume<error descr="Cannot resolve method 'consume(java.io.Serializable)'">(getSerizalizable())</error>;
    consume<error descr="Cannot resolve method 'consume(java.lang.Object)'">(getObject())</error>;
  }

  public static <T extends Issue> T get() {
    return null;
  }

  public static <T extends Serializable> T getSerizalizable() {
    return null;
  }
  
  public static <T> T getObject() {
    return null;
  }

  public static void consume(Issue... v) {}

  public static void consume(Collection v) {}

}