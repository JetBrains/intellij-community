class Scratch
{
  public static class PredicatedProposal<T>
  {
    private final Discriminator<? super T> pred = null;

    public Discriminator<? super T> get()
    {
      return this.pred;
    }
  }

  public static interface Discriminator<T extends String> extends Predicate<T>
  {

  }

  interface Predicate<T> {
    boolean val(T t);
  }
}