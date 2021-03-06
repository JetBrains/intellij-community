import org.jetbrains.annotations.*;

class CandidateInfo<T> {
  @Contract("null, true, _ -> fail")
  private CandidateInfo(@Nullable Object candidate, boolean applicable, boolean fullySubstituted) {
    assert !applicable || candidate != null;
  }

  static void test() {
    if (Math.random() > 0.5) new <warning descr="The call to 'CandidateInfo' always fails, according to its method contracts">CandidateInfo</warning>(null, true, false);
    if (Math.random() > 0.5) new <warning descr="The call to 'CandidateInfo' always fails, according to its method contracts">CandidateInfo</warning>(null, true, true);

    if (Math.random() > 0.5) new CandidateInfo(null, false, true);
    if (Math.random() > 0.5) new CandidateInfo(new Object(), true, true);
    if (Math.random() > 0.5) new CandidateInfo(new Object(), false, true);
  }
}