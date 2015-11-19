public class Bug
{

  interface I<K>{}
  public static <CRN>I<CRN> create(final I<CRN> it)
  {
    final I<CRN> f = null;

    Bug.<String>create<error descr="'create(Bug.I<java.lang.String>)' in 'Bug' cannot be applied to '(Bug.I<CRN>)'">(fn(f))</error>;

    return create(fn(f));

  }

  private static <FN>I<FN> fn(final I<FN> f)
  {
    return null;
  }
}