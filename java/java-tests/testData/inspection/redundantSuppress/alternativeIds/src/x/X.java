package x;

class S {
  @SuppressWarnings("RawUseOfParameterized")
  public String get(final Class cls)
  {
    return "";
  }
}

@SuppressWarnings("UnusedDeclaration")
class Bar {}

class NoSuperCall {
    @SuppressWarnings("CloneDoesntCallSuperClone")
    @Override
    public Object clone() {
        return new NoSuperCall();
    }

}

class UnusedParam {
  public static void main(String[] args) {
    foo(1);
  }

  @SuppressWarnings("UnusedParameters")
  static void foo(int i) {}
}