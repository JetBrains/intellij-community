import org.jetbrains.annotations.Nullable;

class Contracts {

  private void check(@Nullable Object o) {
    com.google.common.base.Preconditions.checkNotNull(o);
    System.out.println(o.hashCode());
  }


}