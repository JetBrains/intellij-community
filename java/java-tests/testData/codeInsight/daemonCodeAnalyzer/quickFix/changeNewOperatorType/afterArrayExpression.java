// "Change 'new Integer[10]' to 'new Integer()'" "true"

class X {

  Integer x() {
    return new Integer();
  }
}