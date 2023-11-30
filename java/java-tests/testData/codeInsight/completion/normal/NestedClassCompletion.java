sealed interface AddUserError {

  final class NameIsEmpty implements AddUserError {}

  final class NameIsTooLong implements AddUserError {}
}

class Test {

  public static AddUserError TryAddUser() {
    return new AUE.<caret>
  }
}