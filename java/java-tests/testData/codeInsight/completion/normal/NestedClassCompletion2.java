sealed interface AddUserError {

  non-sealed class NameIsEmpty implements AddUserError {
    final static class T4 extends NameIsTooLong.T3 {}
  }

  non-sealed class NameIsTooLong implements AddUserError {
    static class T extends NameIsEmpty{}
    static class T3 extends T{}
  }
}
class Test {

  public static AddUserError TryAddUser() {
    return new AUE.<caret>
  }
}