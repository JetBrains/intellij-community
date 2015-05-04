import org.jetbrains.annotations.Nullable;

class Contracts {

  private void check(@Nullable Object o, @Nullable Object o2) {
    com.google.common.base.Preconditions.checkArgument(o != null);
    com.google.common.base.Preconditions.checkState(o2 != null, "");
    System.out.println(o.hashCode());
    System.out.println(o2.hashCode());
  }


}