// "Unimplement" "true-preview"
class X implements Comparable<String<caret>> {
  @Override
  public int compareTo(String o) {
    return 0;
  }
}