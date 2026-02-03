// "Transform body to single exit-point form" "true-preview"
class X {
  private static Integer <caret>testMultipleReturnPoints() {
    try {
      List<Integer> integers = new ArrayList<>();

      for (Integer integer : integers) {
        if (integer == 1) {
          return integer;
        }
        else if (integer == 2) {
          return integer;
        }
      }

      return null;
    }
    finally {

    }
  }
}