import codeanalysis.experimental.annotations.DefaultNotNull;

@DefaultNotNull
class X {
  X get() {
    return /*ca-nullable-to-not-null*/null;
  }
}