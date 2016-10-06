import org.jetbrains.annotations.*;

class CandidateInfo<T> {
  @Contract("null, true, _ -> fail")
  private CandidateInfo(@Nullable Object candidate, boolean applicable, boolean fullySubstituted) {
    assert !applicable || candidate != null;
  }

  static void test() {
    new <warning descr="The call to CandidateInfo always fails, according to its method contracts">CandidateInfo</warning>(null, true, false);
    new <warning descr="The call to CandidateInfo always fails, according to its method contracts">CandidateInfo</warning>(null, true, true);

    new CandidateInfo(null, false, true);
    new CandidateInfo(new Object(), true, true);
    new CandidateInfo(new Object(), false, true);
  }
}