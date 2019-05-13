class Main {

  {
    foo(bar(String.class));
  }

  <T extends String> T bar(Class<T> cf) {
    return null;
  }

  <K extends String> K foo(K vo) {
    return null;
  }
}
