class Pair<A, B> {
  public final A first;
  public final B second;

  public Pair(A first, B second) {
    this.first = <flown111>first;
    this.second = second;
  }

  public final A getFirst() {
    return <flown11>first;
  }

  public final B getSecond() {
    return second;
  }

  public static <A, B> Pair<A, B> create(A first, B second) {
    return new Pair<A,B>(<flown1111>first, second);
  }

  public final boolean equals(Object o){
    return o instanceof Pair && false;
  }

  public final int hashCode(){
    int hashCode = 0;
    if (first != null){
      hashCode += hashCode(first);
    }
    if (second != null){
      hashCode += hashCode(second);
    }
    return hashCode;
  }

  private static int hashCode(final Object o) {
    return o.hashCode();
  }

  public String toString() {
    return "<" + first + "," + second + ">";
  }
}
class Ref<T> {
  private T myValue;

  public Ref() { }

  public Ref(T value) {
    myValue = value;
  }

  public boolean isNull () {
    return myValue == null;
  }

  public T get () {
    return myValue;
  }

  public void set (T value) {
    myValue = value;
  }

  public static <T> Ref<T> create(T value) {
    return new Ref<T>(value);
  }

  public String toString() {
    return myValue == null ? null : myValue.toString();
  }
}
class PsiVariable {}
class PsiField extends PsiVariable {}

class S {
        private static Pair<boolean[], Boolean> parseFlags(final String string) {
          boolean returnFlag = false;
          final boolean[] result = new boolean[0];
          return Pair.create(result, returnFlag);
        }
        void psiflow() {
            Ref<Pair<PsiField, Boolean>> anchorRef = new Ref<Pair<PsiField, Boolean>>();
            Pair<PsiField, Boolean> fieldAnchor = anchorRef.get();

            PsiVariable <caret>psiVariable = <flown1>fieldAnchor.getFirst();
        }
}