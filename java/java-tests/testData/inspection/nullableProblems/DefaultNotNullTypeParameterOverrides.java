import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;

interface Test<T> {
  @NotNull
  T test();
}
@NotNullByDefault
class TestImpl<T> implements Test<T> {

  final T value;

  public TestImpl(final T value) {
    this.value = value;
  }

  @Override
  public T test() {  
    return this.value;
  }
}