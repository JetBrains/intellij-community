// "Remove 'unchecked' suppression" "false"

@SuppressWarnings({"unchecked", "bla-blah-toolid"})
public enum Planet {
  MERCURY(),
  VENUS();

  <T> Plan<caret>et(T... ts) {
  }

}
