// "Replace 'switch' with 'if'" "true-preview"
class Test {
  void m() {
    switc<caret>h (0) {
      case 1 -> {
      }
      default -> {}
    }

  }
}