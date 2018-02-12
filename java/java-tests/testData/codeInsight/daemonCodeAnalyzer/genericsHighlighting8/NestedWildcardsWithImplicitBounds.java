
import java.io.Serializable;
import java.util.List;

interface Child<T extends Serializable> extends List<T> {
  default void m(List<Child<?>> l) {
    List<? extends List<? extends Serializable>> ll =  l;
  }
}