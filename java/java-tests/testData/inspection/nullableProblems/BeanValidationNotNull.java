import javax.annotation.constraints.*;

interface Intf {
  @NotNull Object foo(@NotNull Object p);
}

class Impl implements Intf {
  @Override
  public Object foo(Object p) {
    return p;
  }

}
