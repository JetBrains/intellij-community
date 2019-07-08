// "Replace 'switch' with 'if'" "true"
class Test {
  void m() {
    switc<caret>h (0) {
      case 1 -> {
      }
      default -> {}
    }

  }
}