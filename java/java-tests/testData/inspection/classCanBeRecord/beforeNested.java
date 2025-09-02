// "Convert to record class" "true-preview"

package org.qw;

class ArrayList<T> {

}

// Test for IDEA-310010
public class RecordWithEnum<caret> {

  private final Mode mode;
  private final String name;
  private final Nested nested;
  private final java.util.List<Integer> integers;
  private final java.util.Set<java.lang.String> strings;
  private final java.util.ArrayList<Double> doubles;

  public RecordWithEnum(Mode mode, String name, Nested nested, java.util.List<Integer> integers, java.util.Set<java.lang.String> strings, java.util.ArrayList<Double> doubles) {
    this.mode = mode;
    this.name = name;
    this.nested = nested;
    this.integers = integers;
    this.strings = strings;
    this.doubles = doubles;
  }

  public Mode getMode() {
    return mode;
  }

  public String getName() {
    return name;
  }

  public Nested getNested() {
    return nested;
  }

  public java.util.List<Integer> getIntegers() {
    return integers;
  }

  public java.util.Set<java.lang.String> getStrings() {
    return strings;
  }

  public java.util.ArrayList<Double> getDoubles() {
    return doubles;
  }

  public enum Mode {
    Add, Sub
  }

  public static class Nested {
    int i;
  }
}
