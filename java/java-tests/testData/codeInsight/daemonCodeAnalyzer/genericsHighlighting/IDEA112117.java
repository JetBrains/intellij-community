import java.io.Serializable;
import java.util.Set;

class IdeaGenericsFail {

  public IdeaGenericsFail(Set<Klass<? extends Serializable>> map) {
  }

  public static class Klass<T extends Serializable> {
  }

  public static void main(final Set<Klass<?>> map) {
    new IdeaGenericsFail(map);
  }
}