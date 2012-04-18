import org.jetbrains.annotations.Nullable;

// "Implement method 'foo'" "true"
abstract class Test {
  public abstract void foo(@org.jetbrains.annotations.Nullable String a);
}

class TImple extends Test {
    @Override
    public void foo(@Nullable String a) {
        <selection>//To change body of implemented methods use File | Settings | File Templates.</selection>
    }
}