// "Change 'new Integer[] {1}' to 'new Integer()'" "true"

class X {

  Integer x() {
    return new Integer();
  }
}