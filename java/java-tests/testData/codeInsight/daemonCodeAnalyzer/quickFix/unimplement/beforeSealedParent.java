// "Unimplement" "true-preview"
sealed class A permits B {

}

final class B extends <caret>A {}