// "Remove redundant "unchecked" suppression" "false"

@SuppressWarnings({"unc<caret>hecked", "bla-blah-toolid"})
public enum Planet {
  MERCURY(),
  VENUS();

  <T> Planet(T... ts) {
  }

}
